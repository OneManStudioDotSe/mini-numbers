package se.onemanstudio

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import se.onemanstudio.db.Events
import se.onemanstudio.db.Projects
import se.onemanstudio.models.PageViewPayload
import se.onemanstudio.models.ProjectStats
import se.onemanstudio.models.VisitSnippet
import se.onemanstudio.models.dashboard.ComparisonReport
import se.onemanstudio.models.dashboard.TopPage
import se.onemanstudio.services.GeoLocationService
import se.onemanstudio.utils.AnalyticsSecurity
import se.onemanstudio.utils.UserAgentParser
import java.time.LocalDateTime

fun Application.configureRouting() {
    routing {
        // Serve the Admin HTML
        staticResources("/admin-panel", "static")

        // Admin API
        authenticate("admin-auth") {
            adminRoutes()
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

        get("/") {
            call.respondText("Hello World!")
        }
    }
}

fun Route.adminRoutes() {
    route("/admin") {

        // List all projects
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

        // Create a new project
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

        // Delete a project
        delete("/projects/{id}") {
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            transaction {
                // Also delete all events associated with this project first
                Events.deleteWhere { Events.projectId eq java.util.UUID.fromString(id) }
                Projects.deleteWhere { Projects.id eq java.util.UUID.fromString(id) }
            }
            call.respond(HttpStatusCode.NoContent)
        }

        // Get stats for a specific project
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

        // Get live map for a specific project
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

        // Report endpoint: Returns full analytics report for a time period
        get("/projects/{id}/report") {
            val id = java.util.UUID.fromString(call.parameters["id"])
            val filter = call.request.queryParameters["filter"] ?: "7d"

            val (start, end) = getCurrentPeriod(filter)
            val report = generateReport(id, start, end)

            call.respond(report)
        }

        // Comparison endpoint: Returns current + previous period + time series
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

        // Contribution calendar endpoint: Returns 365 days of activity
        get("/projects/{id}/calendar") {
            val id = java.util.UUID.fromString(call.parameters["id"])
            val calendar = generateContributionCalendar(id)
            call.respond(calendar)
        }
    }
}
