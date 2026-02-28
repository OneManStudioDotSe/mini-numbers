/**
 * Core analytics calculation engine.
 *
 * All functions in this file operate on the [Events] table and produce the
 * data structures that the admin dashboard consumes via the `/report` and
 * `/report/comparison` API endpoints. Results are cached by [se.onemanstudio.middleware.QueryCache]
 * at the routing layer (30 s TTL) so these queries are not re-run on
 * every dashboard refresh.
 *
 * ## Key concepts
 * - **Period helpers** ([getCurrentPeriod], [getPreviousPeriod]): convert a
 *   filter string like `"7d"` into two `LocalDateTime` ranges — the
 *   current window and the immediately preceding window of equal length.
 *   The comparison between the two powers the "↑ 12 %" trend indicators.
 *
 * - **Time series** ([generateTimeSeries]): buckets events into hours,
 *   days, or weeks depending on the selected filter and returns a list of
 *   [TimeSeriesPoint] used by the trend line chart on the dashboard.
 *
 * - **Bounce rate** ([calculateBounceRate]): a session is "bounced" if it
 *   contains only one unique page AND has no heartbeat events (meaning the
 *   visitor left within the first heartbeat interval, typically 30 s).
 *
 * - **Full report** ([generateReport]): the heavyweight function that
 *   assembles a [ProjectReport] with 20+ breakdowns (top pages, browsers,
 *   OS, devices, referrers, countries, regions, UTM, scroll depth, session
 *   metrics, outbound links, file downloads, custom events, activity
 *   heatmap, peak-time analysis, and overall conversion rate).
 *
 * - **Heatmap / calendar** ([generateActivityHeatmap],
 *   [generateContributionCalendar]): produce grid data for the 7 × 24
 *   activity heatmap and the GitHub-style 365-day contribution calendar.
 */
package se.onemanstudio.utils

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import se.onemanstudio.api.models.StatEntry
import se.onemanstudio.api.models.dashboard.*
import se.onemanstudio.db.Events
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*

/**
 * Get the start and end dates for the current period based on filter
 */
fun getCurrentPeriod(filter: String): Pair<LocalDateTime, LocalDateTime> {
    val end = LocalDateTime.now()
    val start = when(filter) {
        "24h" -> end.minusHours(24)
        "3d" -> end.minusDays(3)
        "7d" -> end.minusDays(7)
        "30d" -> end.minusDays(30)
        "365d" -> end.minusDays(365)
        else -> end.minusDays(7)
    }
    return Pair(start, end)
}

/**
 * Get the start and end dates for the previous period (same duration as current)
 */
fun getPreviousPeriod(filter: String): Pair<LocalDateTime, LocalDateTime> {
    val (currentStart, currentEnd) = getCurrentPeriod(filter)
    val duration = Duration.between(currentStart, currentEnd)
    val previousStart = currentStart.minus(duration)
    return Pair(previousStart, currentStart)
}

/**
 * Generate time series data points for trend visualization
 */
fun generateTimeSeries(
    id: UUID,
    start: LocalDateTime,
    end: LocalDateTime,
    filter: String
): List<TimeSeriesPoint> {
    val granularity = when(filter) {
        "24h" -> "hour"
        "3d", "7d" -> "day"
        "30d", "365d" -> "week"
        else -> "day"
    }

    return transaction {
        val events = Events.selectAll()
            .where { (Events.projectId eq id) and (Events.timestamp greaterEq start) and (Events.timestamp lessEq end) }
            .toList()

        // Group by time bucket
        val grouped = events.groupBy { event ->
            val timestamp = event[Events.timestamp]
            when (granularity) {
                "hour" -> timestamp.truncatedTo(ChronoUnit.HOURS)
                "day" -> timestamp.toLocalDate().atStartOfDay()
                "week" -> {
                    // Get the start of the week (Monday)
                    val dayOfWeek = timestamp.dayOfWeek.value
                    val daysToSubtract = if (dayOfWeek == 7) 6 else dayOfWeek - 1
                    timestamp.toLocalDate().minusDays(daysToSubtract.toLong()).atStartOfDay()
                }

                else -> timestamp.toLocalDate().atStartOfDay()
            }
        }

        // Create time series points
        grouped.map { (timestamp, groupedEvents) ->
            TimeSeriesPoint(
                timestamp = timestamp.toString(),
                views = groupedEvents.size.toLong(),
                uniqueVisitors = groupedEvents.map { it[Events.visitorHash] }.distinct().size.toLong()
            )
        }.sortedBy { it.timestamp }
    }
}


/**
 * Calculate bounce rate for a project within a time range.
 * A bounced session = only 1 unique page viewed AND no heartbeat events (left within 30s).
 * Must be called within an existing transaction.
 */
fun calculateBounceRate(id: UUID, start: LocalDateTime, end: LocalDateTime): Double {
    val events = Events.selectAll().where {
        (Events.projectId eq id) and (Events.timestamp greaterEq start) and (Events.timestamp lessEq end)
    }.toList()

    // Group events by session
    val sessions = events.groupBy { it[Events.sessionId] }
    if (sessions.isEmpty()) return 0.0

    val bouncedSessions = sessions.count { (_, sessionEvents) ->
        val uniquePages = sessionEvents.map { it[Events.path] }.distinct().size
        val hasHeartbeat = sessionEvents.any { it[Events.eventType] == "heartbeat" }
        uniquePages == 1 && !hasHeartbeat
    }

    return (bouncedSessions.toDouble() / sessions.size) * 100.0
}

/**
 * Generate a comprehensive project report for the given time window.
 *
 * This is the most expensive query in the application — it touches every
 * event row for the project in the period and computes 20+ breakdowns.
 * Results are cached by [se.onemanstudio.middleware.QueryCache] at the routing layer for 30 seconds.
 *
 * Internally it builds a `baseQuery` (project + time filter) and reuses
 * it via `.copy()` and `.adjustSelect()` to avoid redundant WHERE clauses.
 * The helper `getBreakdown(col)` produces a generic top-10 bar-chart
 * breakdown for any column (path, browser, OS, device, referrer, country).
 *
 * Session-level metrics (avg duration, entry/exit pages, conversion rate)
 * are computed by grouping the full event list by `sessionId` in memory.
 *
 * @param id    Project UUID.
 * @param start Inclusive start of the reporting window.
 * @param end   Inclusive end of the reporting window.
 * @return A fully populated [ProjectReport] ready for JSON serialization.
 */
fun generateReport(id: UUID, start: LocalDateTime, end: LocalDateTime): ProjectReport {
    return transaction {
        val baseQuery = Events.selectAll().where {
            (Events.projectId eq id) and (Events.timestamp greaterEq start) and (Events.timestamp lessEq end)
        }

        val totalViews = baseQuery.count()

        val uniqueVisitors = baseQuery.copy()
            .adjustSelect { this.select(Events.visitorHash) }
            .withDistinct()
            .count()

        fun getBreakdown(col: Column<*>): List<StatEntry> {
            val countCol = col.count()
            return baseQuery.copy()
                .adjustSelect { this.select(col, countCol) }
                .groupBy(col)
                .orderBy(countCol, SortOrder.DESC)
                .limit(10)
                .map { StatEntry(it[col]?.toString() ?: "Unknown", it[countCol]) }
        }

        val activityHeatmap = generateActivityHeatmap(id, start)
        val peakTimeAnalysis = analyzePeakTimes(activityHeatmap)
        val bounceRate = calculateBounceRate(id, start, end)

        // Custom events breakdown (separate query since it filters by eventType)
        val customEventsCountCol = Events.eventName.count()
        val customEvents = Events.select(Events.eventName, customEventsCountCol).where {
            (Events.projectId eq id) and
            (Events.timestamp greaterEq start) and
            (Events.timestamp lessEq end) and
            (Events.eventType eq "custom") and
            Events.eventName.isNotNull()
        }.groupBy(Events.eventName)
            .orderBy(customEventsCountCol, SortOrder.DESC)
            .limit(10)
            .map { StatEntry(it[Events.eventName] ?: "Unknown", it[customEventsCountCol]) }

        // UTM campaign breakdowns
        val utmSources = getBreakdown(Events.utmSource).filter { it.label != "Unknown" }
        val utmMediums = getBreakdown(Events.utmMedium).filter { it.label != "Unknown" }
        val utmCampaigns = getBreakdown(Events.utmCampaign).filter { it.label != "Unknown" }

        // Scroll depth distribution
        val scrollDepthCountCol = Events.scrollDepth.count()
        val scrollDepthDistribution = Events.select(Events.scrollDepth, scrollDepthCountCol).where {
            (Events.projectId eq id) and
            (Events.timestamp greaterEq start) and
            (Events.timestamp lessEq end) and
            (Events.eventType eq "scroll") and
            Events.scrollDepth.isNotNull()
        }.groupBy(Events.scrollDepth)
            .orderBy(Events.scrollDepth, SortOrder.ASC)
            .map { StatEntry("${it[Events.scrollDepth]}%", it[scrollDepthCountCol]) }

        // Session metrics: count, duration, entry/exit pages, conversion rate
        val allEvents = baseQuery.copy().toList()
        val sessions = allEvents.groupBy { it[Events.sessionId] }
        val totalSessions = sessions.size.toLong()

        val avgSessionDuration = if (sessions.isNotEmpty()) {
            sessions.values.map { sessionEvents ->
                val heartbeats = sessionEvents.count { it[Events.eventType] == "heartbeat" }
                (heartbeats * 30).toLong() // 30s heartbeat interval
            }.average()
        } else 0.0

        // Entry pages: first pageview per session
        val entryPages = sessions.values.mapNotNull { sessionEvents ->
            sessionEvents.filter { it[Events.eventType] == "pageview" }
                .minByOrNull { it[Events.timestamp] }
                ?.get(Events.path)
        }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .take(10)
            .map { StatEntry(it.key, it.value.toLong()) }

        // Exit pages: last pageview per session
        val exitPages = sessions.values.mapNotNull { sessionEvents ->
            sessionEvents.filter { it[Events.eventType] == "pageview" }
                .maxByOrNull { it[Events.timestamp] }
                ?.get(Events.path)
        }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .take(10)
            .map { StatEntry(it.key, it.value.toLong()) }

        // Outbound links
        val outboundCountCol = Events.targetUrl.count()
        val outboundLinks = Events.select(Events.targetUrl, outboundCountCol).where {
            (Events.projectId eq id) and
            (Events.timestamp greaterEq start) and
            (Events.timestamp lessEq end) and
            (Events.eventType eq "outbound") and
            Events.targetUrl.isNotNull()
        }.groupBy(Events.targetUrl)
            .orderBy(outboundCountCol, SortOrder.DESC)
            .limit(10)
            .map { StatEntry(it[Events.targetUrl] ?: "Unknown", it[outboundCountCol]) }

        // File downloads
        val downloadCountCol = Events.targetUrl.count()
        val fileDownloads = Events.select(Events.targetUrl, downloadCountCol).where {
            (Events.projectId eq id) and
            (Events.timestamp greaterEq start) and
            (Events.timestamp lessEq end) and
            (Events.eventType eq "download") and
            Events.targetUrl.isNotNull()
        }.groupBy(Events.targetUrl)
            .orderBy(downloadCountCol, SortOrder.DESC)
            .limit(10)
            .map { StatEntry(it[Events.targetUrl] ?: "Unknown", it[downloadCountCol]) }

        // Regions/states — include country for context
        val regionCountCol = Events.region.count()
        val regions = baseQuery.copy()
            .adjustSelect { this.select(Events.country, Events.region, regionCountCol) }
            .groupBy(Events.country, Events.region)
            .orderBy(regionCountCol, SortOrder.DESC)
            .limit(10)
            .filter { it[Events.region] != null && it[Events.region] != "Unknown" }
            .map { StatEntry("${it[Events.region]}, ${it[Events.country] ?: "Unknown"}", it[regionCountCol]) }

        // Overall conversion rate: sessions with at least one custom event / total sessions
        val sessionsWithConversion = sessions.count { (_, events) ->
            events.any { it[Events.eventType] == "custom" }
        }
        val conversionRate = if (totalSessions > 0) {
            (sessionsWithConversion.toDouble() / totalSessions) * 100.0
        } else 0.0

        ProjectReport(
            totalViews = totalViews,
            uniqueVisitors = uniqueVisitors,
            topPages = getBreakdown(Events.path),
            browsers = getBreakdown(Events.browser),
            oss = getBreakdown(Events.os),
            devices = getBreakdown(Events.device),
            referrers = getBreakdown(Events.referrer),
            countries = getBreakdown(Events.country),
            customEvents = customEvents,
            lastVisits = baseQuery.copy()
                .orderBy(Events.timestamp, SortOrder.DESC)
                .limit(10)
                .map {
                    VisitSnippet(
                        path = it[Events.path],
                        timestamp = it[Events.timestamp].toString(),
                        city = it[Events.city],
                        country = it[Events.country]
                    )
                },
            activityHeatmap = activityHeatmap,
            peakTimeAnalysis = peakTimeAnalysis,
            bounceRate = bounceRate,
            utmSources = utmSources,
            utmMediums = utmMediums,
            utmCampaigns = utmCampaigns,
            scrollDepthDistribution = scrollDepthDistribution,
            totalSessions = totalSessions,
            avgSessionDuration = avgSessionDuration,
            entryPages = entryPages,
            exitPages = exitPages,
            outboundLinks = outboundLinks,
            fileDownloads = fileDownloads,
            regions = regions,
            conversionRate = conversionRate
        )
    }
}

/**
 * Generate activity heatmap data (7 days × 24 hours)
 */
fun generateActivityHeatmap(projectId: UUID, cutoff: LocalDateTime): List<ActivityCell> {
    return transaction {
        val events = Events.selectAll()
            .where { (Events.projectId eq projectId) and (Events.timestamp greaterEq cutoff) }
            .toList()

        // Group by day of week and hour
        val grouped = events.groupBy { event ->
            val timestamp = event[Events.timestamp]
            val dayOfWeek = timestamp.dayOfWeek.value % 7  // 0=Sunday, 1=Monday, ..., 6=Saturday
            val hour = timestamp.hour
            Pair(dayOfWeek, hour)
        }

        // Create activity cells
        grouped.map { entry ->
            val (day, hour) = entry.key
            ActivityCell(
                dayOfWeek = day,
                hourOfDay = hour,
                count = entry.value.size.toLong()
            )
        }
    }
}

/**
 * Analyze heatmap data to identify peak traffic times
 */
fun analyzePeakTimes(heatmapData: List<ActivityCell>): PeakTimeAnalysis {
    // Group by hour
    val hourTotals = heatmapData.groupBy { it.hourOfDay }
        .map { (hour, cells) ->
            StatEntry(
                label = "${hour}:00",
                value = cells.sumOf { it.count }
            )
        }
        .sortedByDescending { it.value }

    // Group by day
    val dayNames = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    val dayTotals = heatmapData.groupBy { it.dayOfWeek }
        .map { (day, cells) ->
            StatEntry(
                label = dayNames[day],
                value = cells.sumOf { it.count }
            )
        }
        .sortedByDescending { it.value }

    return PeakTimeAnalysis(
        topHours = hourTotals.take(5),
        topDays = dayTotals.take(3),
        peakHour = heatmapData.maxByOrNull { it.count }?.hourOfDay ?: 0,
        peakDay = heatmapData.maxByOrNull { it.count }?.dayOfWeek ?: 0
    )
}

/**
 * Generate contribution calendar (last 365 days)
 */
fun generateContributionCalendar(projectId: UUID): ContributionCalendar {
    return transaction {
        val endDate = LocalDateTime.now()
        val startDate = endDate.minusDays(365)

        val events = Events.selectAll()
            .where { (Events.projectId eq projectId) and (Events.timestamp greaterEq startDate) }
            .toList()

        // Group by date
        val dailyData = events.groupBy { it[Events.timestamp].toLocalDate() }
            .map { (date, dayEvents) ->
                Triple(
                    date,
                    dayEvents.size.toLong(),
                    dayEvents.map { it[Events.visitorHash] }.distinct().size.toLong()
                )
            }

        val maxVisits = dailyData.maxOfOrNull { it.second } ?: 1L

        // Calculate intensity levels (0-4 scale like GitHub)
        val days = dailyData.map { (date, visits, uniqueVisitors) ->
            val level = when {
                visits == 0L -> 0
                visits < maxVisits * 0.25 -> 1
                visits < maxVisits * 0.50 -> 2
                visits < maxVisits * 0.75 -> 3
                else -> 4
            }

            ContributionDay(
                date = date.toString(),
                visits = visits,
                uniqueVisitors = uniqueVisitors,
                level = level
            )
        }.sortedBy { it.date }

        ContributionCalendar(
            days = days,
            maxVisits = maxVisits,
            startDate = startDate.toLocalDate().toString(),
            endDate = endDate.toLocalDate().toString()
        )
    }
}
