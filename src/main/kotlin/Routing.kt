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
import se.onemanstudio.config.AppConfig
import se.onemanstudio.db.Events
import se.onemanstudio.db.Projects
import se.onemanstudio.middleware.RateLimiter
import se.onemanstudio.middleware.RateLimitResult
import se.onemanstudio.models.PageViewPayload
import se.onemanstudio.models.ProjectStats
import se.onemanstudio.models.VisitSnippet
import se.onemanstudio.models.dashboard.ComparisonReport
import se.onemanstudio.models.dashboard.TopPage
import se.onemanstudio.services.GeoLocationService
import se.onemanstudio.utils.AnalyticsSecurity
import se.onemanstudio.utils.InputValidator
import se.onemanstudio.utils.UserAgentParser
import java.time.LocalDateTime

/**
 * Configure application routing
 * Sets up data collection endpoint, admin panel, and static resources
 *
 * @param config Application configuration for rate limiting and security
 */
fun Application.configureRouting(config: AppConfig) {
    // Initialize rate limiter with configured limits
    val rateLimiter = RateLimiter(
        maxTokensPerIp = config.rateLimit.perIpRequestsPerMinute,
        maxTokensPerApiKey = config.rateLimit.perApiKeyRequestsPerMinute
    )

    routing {
        // Serve the Admin HTML
        staticResources("/admin-panel", "static")

        // Admin API
        authenticate("admin-auth") {
            adminRoutes()
        }

        // Data Collection Endpoint
        post("/collect") {
            // 1. Extract and validate API key
            val apiKey = call.request.headers["X-Project-Key"]
                ?: call.request.queryParameters["key"]
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Missing API key")
                )

            // 2. Get client IP for rate limiting
            val ip = call.request.origin.remoteHost

            // 3. Check rate limits (both IP and API key must pass)
            when (val rateLimitResult = rateLimiter.checkRateLimit(ip, apiKey)) {
                is RateLimitResult.Exceeded -> {
                    call.application.environment.log.warn(
                        "Rate limit exceeded: ${rateLimitResult.limitType} - ${rateLimitResult.identifier}"
                    )
                    return@post call.respond(
                        HttpStatusCode.TooManyRequests,
                        mapOf(
                            "error" to "Rate limit exceeded",
                            "message" to "Too many requests. Please try again later.",
                            "limit_type" to rateLimitResult.limitType,
                            "limit" to rateLimitResult.limit,
                            "window" to rateLimitResult.window
                        )
                    )
                }
                else -> {
                    // Rate limit passed, continue processing
                }
            }

            // 4. Deserialize and validate payload
            val payload = try {
                call.receive<PageViewPayload>()
            } catch (e: Exception) {
                call.application.environment.log.warn("Invalid JSON payload: ${e.message}")
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid request payload")
                )
            }

            // 5. Validate payload fields
            val validationResult = InputValidator.validatePageViewPayload(payload)
            if (!validationResult.isValid) {
                call.application.environment.log.warn(
                    "Validation failed for API key $apiKey: ${validationResult.errors}"
                )
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf(
                        "error" to "Validation failed",
                        "details" to validationResult.errors
                    )
                )
            }

            // 6. Sanitize inputs before database insert
            val sanitizedPath = InputValidator.sanitize(payload.path)
            val sanitizedReferrer = payload.referrer?.let { InputValidator.sanitize(it) }
            val sanitizedSessionId = InputValidator.sanitize(payload.sessionId)

            // 7. Identify the project
            val project = transaction {
                Projects.selectAll().where { Projects.apiKey eq apiKey }.singleOrNull()
            } ?: return@post call.respond(
                HttpStatusCode.NotFound,
                mapOf("error" to "Invalid API key")
            )

            // 8. Extract visitor info (privacy-preserving)
            val (countryName, cityName) = GeoLocationService.lookup(ip)
            val ua = call.request.headers["User-Agent"] ?: "unknown"
            val vHash = AnalyticsSecurity.generateVisitorHash(ip, ua, project[Projects.id].toString())

            // Parse User-Agent for browser/OS/device info
            val browser = UserAgentParser.parseBrowser(ua)
            val os = UserAgentParser.parseOS(ua)
            val device = UserAgentParser.parseDevice(ua)

            // 9. Save to database
            try {
                transaction {
                    Events.insert {
                        it[projectId] = project[Projects.id]
                        it[visitorHash] = vHash
                        it[sessionId] = sanitizedSessionId
                        it[path] = sanitizedPath
                        it[referrer] = sanitizedReferrer
                        it[eventType] = payload.type
                        it[country] = countryName
                        it[city] = cityName
                        it[Events.browser] = browser
                        it[Events.os] = os
                        it[Events.device] = device
                    }
                }
            } catch (e: Exception) {
                call.application.environment.log.error("Database insert failed: ${e.message}")
                return@post call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to save event")
                )
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

        // Health check endpoint
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
