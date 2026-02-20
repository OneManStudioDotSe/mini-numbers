package se.onemanstudio

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.transactions.transaction
import se.onemanstudio.config.models.AppConfig
import se.onemanstudio.db.ConversionGoals
import se.onemanstudio.db.Events
import se.onemanstudio.db.FunnelSteps
import se.onemanstudio.db.Funnels
import se.onemanstudio.db.Projects
import se.onemanstudio.middleware.RateLimiter
import se.onemanstudio.middleware.models.RateLimitResult
import se.onemanstudio.api.models.PageViewPayload
import se.onemanstudio.api.models.ProjectStats
import se.onemanstudio.api.models.RawEvent
import se.onemanstudio.api.models.RawEventsResponse
import se.onemanstudio.api.models.VisitSnippet
import se.onemanstudio.api.models.GoalRequest
import se.onemanstudio.api.models.GoalResponse
import se.onemanstudio.api.models.FunnelRequest
import se.onemanstudio.api.models.FunnelResponse
import se.onemanstudio.api.models.FunnelStepResponse
import se.onemanstudio.api.models.dashboard.ComparisonReport
import se.onemanstudio.api.models.dashboard.TopPage
import se.onemanstudio.services.GeoLocationService
import se.onemanstudio.core.AnalyticsSecurity
import se.onemanstudio.middleware.InputValidator
import se.onemanstudio.services.UserAgentParser
import se.onemanstudio.core.models.UserSession
import se.onemanstudio.core.verifyCredentials
import se.onemanstudio.config.ConfigLoader
import java.time.LocalDateTime
import java.util.UUID

/**
 * Login request payload
 */
@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

/**
 * Safely parse UUID from string parameter
 * Returns null if parameter is missing or invalid
 *
 * @param value UUID string to parse
 * @return Parsed UUID or null if invalid
 */
private fun safeParseUUID(value: String?): UUID? {
    if (value == null) return null
    return try {
        UUID.fromString(value)
    } catch (e: IllegalArgumentException) {
        null
    }
}

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
        // Serve login page
        staticResources("/login", "static/login") {
            default("login.html")
        }

        // Login endpoint
        post("/api/login") {
            val loginRequest = try {
                call.receive<LoginRequest>()
            } catch (e: Exception) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid request body")
                )
            }

            val config = ConfigLoader.load()
            val isValid = verifyCredentials(
                loginRequest.username,
                loginRequest.password,
                config,
                call.application.environment.log
            )

            if (isValid) {
                // Create session
                call.sessions.set(UserSession(username = loginRequest.username))
                call.respond(
                    HttpStatusCode.OK,
                    buildJsonObject {
                        put("success", true)
                        put("message", "Login successful")
                    }
                )
            } else {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "Invalid username or password")
                )
            }
        }

        // Logout endpoint
        post("/api/logout") {
            call.sessions.clear<UserSession>()
            call.respond(
                HttpStatusCode.OK,
                buildJsonObject {
                    put("success", true)
                    put("message", "Logged out successfully")
                }
            )
        }

        // Serve the Admin HTML
        staticResources("/admin-panel", "static") {
            default("admin.html")
        }

        // Admin API - protected by session auth
        authenticate("admin-session") {
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
                        buildJsonObject {
                            put("error", "Rate limit exceeded")
                            put("message", "Too many requests. Please try again later.")
                            put("limit_type", rateLimitResult.limitType)
                            put("limit", rateLimitResult.limit)
                            put("window", rateLimitResult.window)
                        }
                    )
                }
                else -> {
                    // Rate limit passed, continue processing
                }
            }

            // 4. Deserialize and validate payload
            val payload = try {
                call.receive<PageViewPayload>()
            } catch (e: SerializationException) {
                call.application.environment.log.warn("Invalid JSON payload - serialization error: ${e.message}")
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid JSON structure")
                )
            } catch (e: IllegalArgumentException) {
                call.application.environment.log.warn("Invalid payload data: ${e.message}")
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid payload data: ${e.message}")
                )
            } catch (e: BadRequestException) {
                call.application.environment.log.warn("Bad request: ${e.message}")
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid request payload format")
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
            val sanitizedEventName = payload.eventName?.let { InputValidator.sanitize(it) }

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
                        it[eventName] = sanitizedEventName
                        it[country] = countryName
                        it[city] = cityName
                        it[Events.browser] = browser
                        it[Events.os] = os
                        it[Events.device] = device
                    }
                }
            } catch (e: org.jetbrains.exposed.exceptions.ExposedSQLException) {
                call.application.environment.log.error("Database SQL error: ${e.message}", e)
                return@post call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to save event - database error")
                )
            } catch (e: java.sql.SQLException) {
                call.application.environment.log.error("Database connection error: ${e.message}", e)
                return@post call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to save event - database unavailable")
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

        // Note: Root "/" endpoint is handled by SetupRouting.configureSetupRouting()
        // which provides intelligent routing based on setup status and service state
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

        // Update a project
        post("/projects/{id}") {
            val uuid = safeParseUUID(call.parameters["id"])
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid or missing project ID")
                )

            val updates = call.receive<Map<String, String>>()
            val newName = updates["name"]?.trim()

            if (newName.isNullOrBlank()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Project name is required")
                )
            }

            transaction {
                Projects.update({ Projects.id eq uuid }) {
                    it[name] = newName
                }
            }
            call.respond(HttpStatusCode.OK, buildJsonObject { put("success", true) })
        }

        // Delete a project
        delete("/projects/{id}") {
            val uuid = safeParseUUID(call.parameters["id"])
                ?: return@delete call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid or missing project ID")
                )

            transaction {
                // Delete funnel steps for all funnels in this project
                val funnelIds = Funnels.selectAll().where { Funnels.projectId eq uuid }
                    .map { it[Funnels.id] }
                if (funnelIds.isNotEmpty()) {
                    FunnelSteps.deleteWhere { FunnelSteps.funnelId inList funnelIds }
                }
                Funnels.deleteWhere { Funnels.projectId eq uuid }
                ConversionGoals.deleteWhere { ConversionGoals.projectId eq uuid }
                // Delete all events associated with this project
                Events.deleteWhere { Events.projectId eq uuid }
                Projects.deleteWhere { Projects.id eq uuid }
            }
            call.respond(HttpStatusCode.NoContent)
        }

        // Get stats for a specific project
        get("/projects/{id}/stats") {
            val pid = safeParseUUID(call.parameters["id"])
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid or missing project ID")
                )

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
            val id = safeParseUUID(call.parameters["id"])
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid or missing project ID")
                )
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

        // Generate demo data for a project
        post("/projects/{id}/demo-data") {
            val id = safeParseUUID(call.parameters["id"])
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid or missing project ID")
                )
            val request = call.receive<Map<String, Int>>()
            val count = request["count"] ?: 500
            val timeScope = request["timeScope"] ?: 30 // days

            if (count < 0 || count > 3000) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Count must be between 0 and 3000"))
                return@post
            }

            val generated = transaction {
                generateDemoData(id, count, timeScope)
            }

            call.respond(mapOf("generated" to generated))
        }

        // Report endpoint: Returns full analytics report for a time period
        get("/projects/{id}/report") {
            val id = safeParseUUID(call.parameters["id"])
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid or missing project ID")
                )
            val filter = call.request.queryParameters["filter"] ?: "7d"

            val (start, end) = getCurrentPeriod(filter)
            val report = generateReport(id, start, end)

            call.respond(report)
        }

        // Comparison endpoint: Returns current + previous period + time series
        get("/projects/{id}/report/comparison") {
            val id = safeParseUUID(call.parameters["id"])
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid or missing project ID")
                )
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
            val id = safeParseUUID(call.parameters["id"])
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid or missing project ID")
                )
            val calendar = generateContributionCalendar(id)
            call.respond(calendar)
        }

        // Raw events viewer with pagination and filtering
        get("/projects/{id}/events") {
            val id = safeParseUUID(call.parameters["id"])
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid project ID")
                )

            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
            val filterType = call.request.queryParameters["filter"]
            val sortBy = call.request.queryParameters["sortBy"] ?: "timestamp"
            val sortOrder = call.request.queryParameters["order"] ?: "desc"

            val response = transaction {
                val baseQuery = { Events.selectAll().where {
                    if (filterType != null) {
                        (Events.projectId eq id) and (Events.eventType eq filterType)
                    } else {
                        Events.projectId eq id
                    }
                }}

                val total = baseQuery().count()

                val sortColumn = when(sortBy) {
                    "path" -> Events.path
                    "country" -> Events.country
                    "browser" -> Events.browser
                    else -> Events.timestamp
                }

                val order = if (sortOrder == "asc") SortOrder.ASC else SortOrder.DESC

                val results = baseQuery()
                    .orderBy(sortColumn, order)
                    .limit(limit, offset = (page * limit).toLong())
                    .map { row ->
                        RawEvent(
                            id = row[Events.id],
                            timestamp = row[Events.timestamp].toString(),
                            eventType = row[Events.eventType],
                            eventName = row[Events.eventName],
                            path = row[Events.path],
                            referrer = row[Events.referrer],
                            country = row[Events.country],
                            city = row[Events.city],
                            browser = row[Events.browser],
                            os = row[Events.os],
                            device = row[Events.device],
                            sessionId = row[Events.sessionId],
                            duration = row[Events.duration]
                        )
                    }

                RawEventsResponse(
                    events = results,
                    total = total,
                    page = page,
                    limit = limit
                )
            }

            call.respond(response)
        }

        // ── Conversion Goals ──────────────────────────────────────────

        // List all goals for a project
        get("/projects/{id}/goals") {
            val pid = safeParseUUID(call.parameters["id"])
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid or missing project ID")
                )

            val goals = transaction {
                ConversionGoals.selectAll().where { ConversionGoals.projectId eq pid }
                    .orderBy(ConversionGoals.createdAt, SortOrder.DESC)
                    .map {
                        GoalResponse(
                            id = it[ConversionGoals.id].toString(),
                            name = it[ConversionGoals.name],
                            goalType = it[ConversionGoals.goalType],
                            matchValue = it[ConversionGoals.matchValue],
                            isActive = it[ConversionGoals.isActive],
                            createdAt = it[ConversionGoals.createdAt].toString()
                        )
                    }
            }
            call.respond(goals)
        }

        // Create a new goal
        post("/projects/{id}/goals") {
            val pid = safeParseUUID(call.parameters["id"])
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid or missing project ID")
                )

            val request = try {
                call.receive<GoalRequest>()
            } catch (e: Exception) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid request body")
                )
            }

            if (request.name.isBlank() || request.matchValue.isBlank()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Name and match value are required")
                )
            }

            if (request.goalType !in listOf("url", "event")) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Goal type must be 'url' or 'event'")
                )
            }

            val goalId = UUID.randomUUID()
            transaction {
                ConversionGoals.insert {
                    it[id] = goalId
                    it[projectId] = pid
                    it[name] = request.name.trim()
                    it[goalType] = request.goalType
                    it[matchValue] = request.matchValue.trim()
                }
            }

            call.respond(HttpStatusCode.Created, buildJsonObject {
                put("id", goalId.toString())
                put("success", true)
            })
        }

        // Update a goal
        put("/projects/{id}/goals/{goalId}") {
            val pid = safeParseUUID(call.parameters["id"])
                ?: return@put call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid or missing project ID")
                )
            val goalId = safeParseUUID(call.parameters["goalId"])
                ?: return@put call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid or missing goal ID")
                )

            val updates = call.receive<Map<String, String>>()

            transaction {
                ConversionGoals.update({
                    (ConversionGoals.id eq goalId) and (ConversionGoals.projectId eq pid)
                }) {
                    updates["name"]?.let { name -> it[ConversionGoals.name] = name.trim() }
                    updates["goalType"]?.let { type -> it[ConversionGoals.goalType] = type }
                    updates["matchValue"]?.let { value -> it[ConversionGoals.matchValue] = value.trim() }
                    updates["isActive"]?.let { active -> it[ConversionGoals.isActive] = active.toBoolean() }
                }
            }
            call.respond(HttpStatusCode.OK, buildJsonObject { put("success", true) })
        }

        // Delete a goal
        delete("/projects/{id}/goals/{goalId}") {
            val pid = safeParseUUID(call.parameters["id"])
                ?: return@delete call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid or missing project ID")
                )
            val goalId = safeParseUUID(call.parameters["goalId"])
                ?: return@delete call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid or missing goal ID")
                )

            transaction {
                ConversionGoals.deleteWhere {
                    (ConversionGoals.id eq goalId) and (ConversionGoals.projectId eq pid)
                }
            }
            call.respond(HttpStatusCode.NoContent)
        }

        // Get goal stats with conversion rates
        get("/projects/{id}/goals/stats") {
            val pid = safeParseUUID(call.parameters["id"])
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid or missing project ID")
                )
            val filter = call.request.queryParameters["filter"] ?: "7d"

            val stats = calculateGoalStats(pid, filter)
            call.respond(stats)
        }

        // ── Funnels ──────────────────────────────────────────────────

        // List all funnels for a project
        get("/projects/{id}/funnels") {
            val pid = safeParseUUID(call.parameters["id"])
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid or missing project ID")
                )

            val funnels = transaction {
                Funnels.selectAll().where { Funnels.projectId eq pid }
                    .orderBy(Funnels.createdAt, SortOrder.DESC)
                    .map { funnel ->
                        val funnelId = funnel[Funnels.id]
                        val steps = FunnelSteps.selectAll()
                            .where { FunnelSteps.funnelId eq funnelId }
                            .orderBy(FunnelSteps.stepNumber, SortOrder.ASC)
                            .map { step ->
                                FunnelStepResponse(
                                    id = step[FunnelSteps.id].toString(),
                                    stepNumber = step[FunnelSteps.stepNumber],
                                    name = step[FunnelSteps.name],
                                    stepType = step[FunnelSteps.stepType],
                                    matchValue = step[FunnelSteps.matchValue]
                                )
                            }

                        FunnelResponse(
                            id = funnelId.toString(),
                            name = funnel[Funnels.name],
                            steps = steps,
                            createdAt = funnel[Funnels.createdAt].toString()
                        )
                    }
            }
            call.respond(funnels)
        }

        // Create a new funnel with steps
        post("/projects/{id}/funnels") {
            val pid = safeParseUUID(call.parameters["id"])
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid or missing project ID")
                )

            val request = try {
                call.receive<FunnelRequest>()
            } catch (e: Exception) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid request body")
                )
            }

            if (request.name.isBlank()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Funnel name is required")
                )
            }

            if (request.steps.size < 2) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Funnel must have at least 2 steps")
                )
            }

            for (step in request.steps) {
                if (step.stepType !in listOf("url", "event")) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Step type must be 'url' or 'event'")
                    )
                }
            }

            val funnelId = UUID.randomUUID()
            transaction {
                Funnels.insert {
                    it[id] = funnelId
                    it[projectId] = pid
                    it[name] = request.name.trim()
                }

                request.steps.forEachIndexed { index, step ->
                    FunnelSteps.insert {
                        it[id] = UUID.randomUUID()
                        it[FunnelSteps.funnelId] = funnelId
                        it[stepNumber] = index + 1
                        it[name] = step.name.trim()
                        it[stepType] = step.stepType
                        it[matchValue] = step.matchValue.trim()
                    }
                }
            }

            call.respond(HttpStatusCode.Created, buildJsonObject {
                put("id", funnelId.toString())
                put("success", true)
            })
        }

        // Delete a funnel and its steps
        delete("/projects/{id}/funnels/{funnelId}") {
            val pid = safeParseUUID(call.parameters["id"])
                ?: return@delete call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid or missing project ID")
                )
            val funnelId = safeParseUUID(call.parameters["funnelId"])
                ?: return@delete call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid or missing funnel ID")
                )

            transaction {
                FunnelSteps.deleteWhere { FunnelSteps.funnelId eq funnelId }
                Funnels.deleteWhere {
                    (Funnels.id eq funnelId) and (Funnels.projectId eq pid)
                }
            }
            call.respond(HttpStatusCode.NoContent)
        }

        // Funnel analysis with drop-off rates
        get("/projects/{id}/funnels/{funnelId}/analysis") {
            val pid = safeParseUUID(call.parameters["id"])
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid or missing project ID")
                )
            val funnelId = safeParseUUID(call.parameters["funnelId"])
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid or missing funnel ID")
                )
            val filter = call.request.queryParameters["filter"] ?: "7d"

            val (start, end) = getCurrentPeriod(filter)
            val analysis = analyzeFunnel(funnelId, pid, start, end)
            call.respond(analysis)
        }
    }
}

/**
 * Generate realistic demo/dummy data for testing
 * @param projectId Project UUID
 * @param count Number of events to generate (0-3000)
 * @param timeScope Number of days to spread events over (default: 30)
 * @return Number of events actually generated
 */
private fun generateDemoData(projectId: java.util.UUID, count: Int, timeScope: Int = 30): Int {
    if (count == 0) return 0

    val paths = listOf(
        "/", "/about", "/contact", "/blog", "/products", "/services",
        "/blog/getting-started", "/blog/tutorial", "/blog/announcement",
        "/products/item-1", "/products/item-2", "/products/item-3",
        "/pricing", "/faq", "/docs", "/docs/api", "/docs/guide"
    )

    val referrers = listOf(
        null, // Direct traffic
        "https://google.com/search",
        "https://twitter.com",
        "https://facebook.com",
        "https://github.com",
        "https://reddit.com",
        "https://linkedin.com",
        "https://news.ycombinator.com"
    )

    val customEventNames = listOf("signup", "download", "purchase", "newsletter_subscribe", "share", "contact_form")

    val browsers = listOf("Chrome", "Firefox", "Safari", "Edge", "Opera")
    val oses = listOf("Windows", "macOS", "Linux", "iOS", "Android")
    val devices = listOf("Desktop", "Mobile", "Tablet")

    val countries = listOf("United States", "United Kingdom", "Canada", "Germany", "France", "Spain", "Italy", "Australia", "Japan", "Brazil")
    val cities = mapOf(
        "United States" to listOf("New York", "Los Angeles", "Chicago", "Houston", "Phoenix"),
        "United Kingdom" to listOf("London", "Manchester", "Birmingham", "Leeds", "Glasgow"),
        "Canada" to listOf("Toronto", "Montreal", "Vancouver", "Calgary", "Ottawa"),
        "Germany" to listOf("Berlin", "Munich", "Hamburg", "Frankfurt", "Cologne"),
        "France" to listOf("Paris", "Marseille", "Lyon", "Toulouse", "Nice"),
        "Spain" to listOf("Madrid", "Barcelona", "Valencia", "Seville", "Zaragoza"),
        "Italy" to listOf("Rome", "Milan", "Naples", "Turin", "Florence"),
        "Australia" to listOf("Sydney", "Melbourne", "Brisbane", "Perth", "Adelaide"),
        "Japan" to listOf("Tokyo", "Osaka", "Yokohama", "Nagoya", "Sapporo"),
        "Brazil" to listOf("São Paulo", "Rio de Janeiro", "Brasília", "Salvador", "Fortaleza")
    )

    val now = LocalDateTime.now()
    val random = java.util.Random()

    var inserted = 0
    var remaining = count

    while (remaining > 0) {
        val daysAgo = random.nextInt(timeScope)
        val sessionStart = now.minusDays(daysAgo.toLong())
            .minusHours(random.nextInt(24).toLong())
            .minusMinutes(random.nextInt(60).toLong())

        val country = countries.random()
        val city = cities[country]?.random()
        val sessionId = java.util.UUID.randomUUID().toString()
        val browser = browsers.random()
        val os = oses.random()
        val device = devices.random()
        val referrer = referrers.random()

        val visitorHash = AnalyticsSecurity.generateVisitorHash(
            ip = "192.168.${random.nextInt(255)}.${random.nextInt(255)}",
            userAgent = browser,
            projectId = projectId.toString()
        )

        // ~40% bounce sessions (single pageview, no heartbeat)
        // ~60% engaged sessions (multiple pageviews + heartbeats)
        val isBounce = random.nextDouble() < 0.4
        val sessionEvents = if (isBounce) 1 else minOf(2 + random.nextInt(4), remaining) // 2-5 events

        var currentTimestamp = sessionStart
        val firstPath = paths.random()

        for (eventIdx in 0 until sessionEvents) {
            if (remaining <= 0) break

            val isFirstEvent = eventIdx == 0
            val eventType: String
            val path: String
            val customEventName: String?

            if (isFirstEvent) {
                eventType = "pageview"
                path = firstPath
                customEventName = null
            } else if (!isBounce && random.nextDouble() < 0.15) {
                // 15% chance of a custom event in engaged sessions
                eventType = "custom"
                path = firstPath
                customEventName = customEventNames.random()
            } else if (!isBounce && random.nextDouble() < 0.4) {
                // 40% chance of navigating to a new page within the session
                eventType = "pageview"
                path = paths.filter { it != firstPath }.random()
                customEventName = null
            } else {
                eventType = "heartbeat"
                path = firstPath
                customEventName = null
            }

            Events.insert {
                it[Events.projectId] = projectId
                it[Events.visitorHash] = visitorHash
                it[Events.sessionId] = sessionId
                it[Events.eventType] = eventType
                it[Events.eventName] = customEventName
                it[Events.path] = path
                it[Events.referrer] = if (isFirstEvent) referrer else null
                it[Events.country] = country
                it[Events.city] = city
                it[Events.browser] = browser
                it[Events.os] = os
                it[Events.device] = device
                it[Events.duration] = if (eventType == "heartbeat") 30 else 0
                it[Events.timestamp] = currentTimestamp
            }

            currentTimestamp = currentTimestamp.plusSeconds(30)
            inserted++
            remaining--
        }
    }

    return inserted
}
