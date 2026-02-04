package se.onemanstudio

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.http.content.resources
import io.ktor.server.http.content.static
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.transactions.transaction
import se.onemanstudio.db.Events
import se.onemanstudio.db.Projects
import se.onemanstudio.models.PageViewPayload
import se.onemanstudio.models.ProjectReport
import se.onemanstudio.models.ProjectStats
import se.onemanstudio.models.StatEntry
import se.onemanstudio.models.TopPage
import se.onemanstudio.models.VisitSnippet
import se.onemanstudio.models.ComparisonReport
import se.onemanstudio.models.TimeSeriesPoint
import se.onemanstudio.models.ActivityCell
import se.onemanstudio.models.PeakTimeAnalysis
import se.onemanstudio.models.ContributionDay
import se.onemanstudio.models.ContributionCalendar
import java.time.Duration
import java.time.temporal.ChronoUnit
import se.onemanstudio.services.GeoLocationService
import se.onemanstudio.utils.AnalyticsSecurity
import se.onemanstudio.utils.UserAgentParser
import java.time.LocalDateTime
import kotlin.to

fun Application.configureRouting() {
    routing {
        static("/static") {
            resources("static")
        }

        // Serve the Admin HTML
        staticResources("/admin-panel", "static")

        // Admin API
        authenticate("admin-auth") {
            adminRoutes()
        }

        get("/") {
            call.respondText("Hello World!")
        }

        // Data Collection
        post("/collect") {
            // Check header OR query parameter
            val apiKey = call.request.headers["X-Project-Key"]
                ?: call.request.queryParameters["key"]
                ?: return@post call.respond(HttpStatusCode.BadRequest)

            val payload = call.receive<PageViewPayload>()

            // 1. Identify the project
            val project = transaction {
                Projects.selectAll().where { Projects.apiKey eq apiKey }.singleOrNull()
            } ?: return@post call.respond(HttpStatusCode.NotFound)

            // 2. Extract visitor info

            val ip = call.request.origin.remoteHost
            val (countryName, cityName) = GeoLocationService.lookup(ip) // LOOKUP HERE

            val ua = call.request.headers["User-Agent"] ?: "unknown"
            val vHash = AnalyticsSecurity.generateVisitorHash(ip, ua, project[Projects.id].toString())

            // Parse User-Agent for browser/OS/device info
            val browser = UserAgentParser.parseBrowser(ua)
            val os = UserAgentParser.parseOS(ua)
            val device = UserAgentParser.parseDevice(ua)

            // 3. Save to DB
            transaction {
                Events.insert {
                    it[projectId] = project[Projects.id]
                    it[visitorHash] = vHash
                    it[sessionId] = payload.sessionId
                    it[path] = payload.path
                    it[referrer] = payload.referrer
                    it[eventType] = payload.type
                    it[country] = countryName // SAVE GEO DATA
                    it[city] = cityName
                    it[Events.browser] = browser // PARSE AND SAVE BROWSER
                    it[Events.os] = os           // PARSE AND SAVE OS
                    it[Events.device] = device   // PARSE AND SAVE DEVICE
                }
            }

            /*
            Why this is a "Privacy-First" approach:
                - Transient PII: The IP address exists in the server's memory for only a few milliseconds.
                - No Storage: The IP is never written to the database or logs.
                - Hashed ID: Even the visitorHash is built using a salt that changes daily,
                             making it impossible to "track" a person across different days.
             */
            call.respond(HttpStatusCode.Accepted)
        }
    }
}

fun Route.adminRoutes() {
    route("/admin") {
        // 1. List all projects
        get("/projects") {
            val allProjects = transaction {
                Projects.selectAll().map {
                    mapOf(
                        "id" to it[Projects.id].toString(),
                        "name" to it[Projects.name],
                        "domain" to it[Projects.domain],
                        "apiKey" to it[Projects.apiKey]
                    )
                }
            }
            call.respond(allProjects)
        }

        // 2. Create a new project
        post("/projects") {
            val params = call.receive<Map<String, String>>()
            val newName = params["name"] ?: return@post call.respond(HttpStatusCode.BadRequest)
            val newDomain = params["domain"] ?: return@post call.respond(HttpStatusCode.BadRequest)

            transaction {
                Projects.insert {
                    it[id] = java.util.UUID.randomUUID()
                    it[name] = newName
                    it[domain] = newDomain
                    it[apiKey] = java.util.UUID.randomUUID().toString().replace("-", "")
                }
            }
            call.respond(HttpStatusCode.Created)
        }

        // 3. Delete a project
        delete("/projects/{id}") {
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            transaction {
                // Also delete all events associated with this project first
                Events.deleteWhere { Events.projectId eq java.util.UUID.fromString(id) }
                Projects.deleteWhere { Projects.id eq java.util.UUID.fromString(id) }
            }
            call.respond(HttpStatusCode.NoContent)
        }

        get("/projects/{id}/stats") {
            val projectIdParam = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val pid = java.util.UUID.fromString(projectIdParam)

            val stats = transaction {
                // 1. Total Page Views
                val totalViews = Events.selectAll().where { Events.projectId eq pid }.count()

                // 2. Unique Visitors
                val uniqueVisitors = Events.select(Events.visitorHash)
                    .where { Events.projectId eq pid }
                    .withDistinct()
                    .count()

                // 3. Top Pages
                val topPages = Events.select(Events.path, Events.path.count())
                    .where { Events.projectId eq pid }
                    .groupBy(Events.path)
                    .orderBy(Events.path.count(), SortOrder.DESC)
                    .limit(5)
                    .map {
                        TopPage(
                            path = it[Events.path],
                            count = it[Events.path.count()]
                        )
                    }

                // Return the typed object instead of a Map
                ProjectStats(
                    totalViews = totalViews,
                    uniqueVisitors = uniqueVisitors,
                    topPages = topPages
                )
            }
            call.respond(stats)
        }

        get("/projects/{id}/live") {
            val id = java.util.UUID.fromString(call.parameters["id"])
            val fiveMinutesAgo = LocalDateTime.now().minusMinutes(5)

            val liveData = transaction {
                Events.selectAll()
                    .where { (Events.projectId eq id) and (Events.timestamp greaterEq fiveMinutesAgo) }
                    .orderBy(Events.timestamp, SortOrder.DESC)
                    .limit(20)
                    .map { VisitSnippet(it[Events.path], it[Events.timestamp].toString(), it[Events.city]) }
            }
            call.respond(liveData)
        }

        // Report endpoint - returns full analytics report for a time period
        get("/projects/{id}/report") {
            val id = java.util.UUID.fromString(call.parameters["id"])
            val filter = call.request.queryParameters["filter"] ?: "7d"

            val (start, end) = getCurrentPeriod(filter)
            val report = generateReport(id, start, end)

            call.respond(report)
        }

        // Comparison endpoint - returns current + previous period + time series
        get("/projects/{id}/report/comparison") {
            val id = java.util.UUID.fromString(call.parameters["id"])
            val filter = call.request.queryParameters["filter"] ?: "7d"

            val (currentStart, currentEnd) = getCurrentPeriod(filter)
            val (previousStart, previousEnd) = getPreviousPeriod(filter)

            val current = generateReport(id, currentStart, currentEnd)
            val previous = generateReport(id, previousStart, previousEnd)
            val timeSeries = generateTimeSeries(id, currentStart, currentEnd, filter)

            val comparisonReport = ComparisonReport(
                current = current,
                previous = previous,
                timeSeries = timeSeries
            )

            call.respond(comparisonReport)
        }

        // Contribution calendar endpoint - returns 365 days of activity
        get("/projects/{id}/calendar") {
            val id = java.util.UUID.fromString(call.parameters["id"])
            val calendar = generateContributionCalendar(id)
            call.respond(calendar)
        }
    }
}

// Helper Functions for Period Comparison and Time Series

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
    id: java.util.UUID,
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
            when(granularity) {
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
 * Generate activity heatmap data (7 days × 24 hours)
 */
fun generateActivityHeatmap(projectId: java.util.UUID, cutoff: LocalDateTime): List<ActivityCell> {
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
fun generateContributionCalendar(projectId: java.util.UUID): ContributionCalendar {
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

/**
 * Generate a full project report for a given time period
 */
fun generateReport(id: java.util.UUID, start: LocalDateTime, end: LocalDateTime): ProjectReport {
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

        ProjectReport(
            totalViews = totalViews,
            uniqueVisitors = uniqueVisitors,
            topPages = getBreakdown(Events.path),
            browsers = getBreakdown(Events.browser),
            oss = getBreakdown(Events.os),
            devices = getBreakdown(Events.device),
            referrers = getBreakdown(Events.referrer),
            countries = getBreakdown(Events.country),
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
            peakTimeAnalysis = peakTimeAnalysis
        )
    }
}
