package se.onemanstudio

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import se.onemanstudio.db.Events
import se.onemanstudio.api.models.ProjectReport
import se.onemanstudio.api.models.StatEntry
import se.onemanstudio.api.models.VisitSnippet
import se.onemanstudio.api.models.dashboard.ActivityCell
import se.onemanstudio.api.models.dashboard.ContributionCalendar
import se.onemanstudio.api.models.dashboard.ContributionDay
import se.onemanstudio.api.models.dashboard.PeakTimeAnalysis
import se.onemanstudio.api.models.dashboard.TimeSeriesPoint
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

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
    val previousEnd = currentStart
    val previousStart = previousEnd.minus(duration)
    return Pair(previousStart, previousEnd)
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
 * Generate a full project report for a given time period
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
        val customEvents = Events.selectAll().where {
            (Events.projectId eq id) and
            (Events.timestamp greaterEq start) and
            (Events.timestamp lessEq end) and
            (Events.eventType eq "custom") and
            Events.eventName.isNotNull()
        }.groupBy(Events.eventName)
            .orderBy(customEventsCountCol, SortOrder.DESC)
            .limit(10)
            .map { StatEntry(it[Events.eventName] ?: "Unknown", it[customEventsCountCol]) }

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
                        city = it[Events.city]
                    )
                },
            activityHeatmap = activityHeatmap,
            peakTimeAnalysis = peakTimeAnalysis,
            bounceRate = bounceRate
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