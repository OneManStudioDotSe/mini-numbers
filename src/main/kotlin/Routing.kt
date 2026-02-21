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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.transactions.transaction
import se.onemanstudio.config.models.AppConfig
import se.onemanstudio.config.models.PrivacyMode
import se.onemanstudio.db.ConversionGoals
import se.onemanstudio.db.Events
import se.onemanstudio.db.FunnelSteps
import se.onemanstudio.db.Funnels
import se.onemanstudio.db.Projects
import se.onemanstudio.db.Segments
import se.onemanstudio.middleware.RateLimiter
import se.onemanstudio.middleware.QueryCache
import se.onemanstudio.middleware.models.RateLimitResult
import se.onemanstudio.api.models.*
import se.onemanstudio.api.models.dashboard.ComparisonReport
import se.onemanstudio.api.models.dashboard.TopPage
import se.onemanstudio.services.GeoLocationService
import se.onemanstudio.core.AnalyticsSecurity
import se.onemanstudio.core.ServiceManager
import se.onemanstudio.middleware.InputValidator
import se.onemanstudio.services.UserAgentParser
import se.onemanstudio.core.models.UserSession
import se.onemanstudio.core.verifyCredentials
import se.onemanstudio.config.ConfigLoader
import java.time.LocalDateTime
import java.util.UUID

@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

private fun safeParseUUID(value: String?): UUID? {
    if (value == null) return null
    return try {
        UUID.fromString(value)
    } catch (e: IllegalArgumentException) {
        null
    }
}

fun Application.configureRouting(config: AppConfig) {
    val rateLimiter = RateLimiter(
        maxTokensPerIp = config.rateLimit.perIpRequestsPerMinute,
        maxTokensPerApiKey = config.rateLimit.perApiKeyRequestsPerMinute
    )
    val privacyMode = config.privacy.privacyMode

    routing {
        // ── Health Check & Metrics ─────────────────────────────────────
        get("/health") {
            val state = ServiceManager.getState()
            val status = if (ServiceManager.isReady()) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
            call.respond(status, buildJsonObject {
                put("status", if (ServiceManager.isReady()) "healthy" else "unhealthy")
                put("state", state.name)
                put("uptime_seconds", ServiceManager.getUptimeSeconds())
                put("version", "1.0.0")
            })
        }

        get("/metrics") {
            if (!ServiceManager.isReady()) {
                return@get call.respond(HttpStatusCode.ServiceUnavailable,
                    ApiError.serviceUnavailable("Services not ready"))
            }
            val dbStats = transaction {
                val totalEvents = Events.selectAll().count()
                val totalProjects = Projects.selectAll().count()
                mapOf("total_events" to totalEvents, "total_projects" to totalProjects)
            }
            call.respond(buildJsonObject {
                put("uptime_seconds", ServiceManager.getUptimeSeconds())
                put("total_events", dbStats["total_events"] ?: 0)
                put("total_projects", dbStats["total_projects"] ?: 0)
                put("cache_size", QueryCache.stats()["size"].toString().toLongOrNull() ?: 0)
                put("hash_rotation_hours", AnalyticsSecurity.getRotationHours())
                put("privacy_mode", config.privacy.privacyMode.name)
                put("data_retention_days", config.privacy.dataRetentionDays)
            })
        }

        // ── Tracker Configuration Endpoint ─────────────────────────────
        get("/tracker/config") {
            call.respond(buildJsonObject {
                put("heartbeatInterval", config.tracker.heartbeatIntervalSeconds)
                put("spaEnabled", config.tracker.spaTrackingEnabled)
            })
        }

        // Serve login page
        staticResources("/login", "static/login") {
            default("login.html")
        }

        // Login endpoint
        post("/api/login") {
            val loginRequest = try {
                call.receive<LoginRequest>()
            } catch (e: Exception) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid request body"))
            }

            val config = ConfigLoader.load()
            val isValid = verifyCredentials(
                loginRequest.username,
                loginRequest.password,
                config,
                call.application.environment.log
            )

            if (isValid) {
                call.sessions.set(UserSession(username = loginRequest.username))
                call.respond(HttpStatusCode.OK, buildJsonObject {
                    put("success", true)
                    put("message", "Login successful")
                })
            } else {
                call.respond(HttpStatusCode.Unauthorized,
                    ApiError.unauthorized("Invalid username or password"))
            }
        }

        // Logout endpoint
        post("/api/logout") {
            call.sessions.clear<UserSession>()
            call.respond(HttpStatusCode.OK, buildJsonObject {
                put("success", true)
                put("message", "Logged out successfully")
            })
        }

        // Serve the Admin HTML
        staticResources("/admin-panel", "static") {
            default("admin.html")
        }

        // Admin API - protected by session auth
        authenticate("admin-session") {
            adminRoutes(privacyMode)
        }

        // Data Collection Endpoint
        post("/collect") {
            val apiKey = call.request.headers["X-Project-Key"]
                ?: call.request.queryParameters["key"]
                ?: return@post call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Missing API key"))

            val ip = call.request.origin.remoteHost

            when (val rateLimitResult = rateLimiter.checkRateLimit(ip, apiKey)) {
                is RateLimitResult.Exceeded -> {
                    call.application.environment.log.warn(
                        "Rate limit exceeded: ${rateLimitResult.limitType} - ${rateLimitResult.identifier}"
                    )
                    return@post call.respond(HttpStatusCode.TooManyRequests,
                        ApiError.rateLimited(
                            "Too many requests. Please try again later.",
                            rateLimitResult.limitType,
                            rateLimitResult.limit,
                            rateLimitResult.window
                        ))
                }
                else -> { /* Rate limit passed */ }
            }

            val payload = try {
                call.receive<PageViewPayload>()
            } catch (e: SerializationException) {
                call.application.environment.log.warn("Invalid JSON payload: ${e.message}")
                return@post call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid JSON structure"))
            } catch (e: IllegalArgumentException) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid payload data: ${e.message}"))
            } catch (e: BadRequestException) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid request payload format"))
            }

            val validationResult = InputValidator.validatePageViewPayload(payload)
            if (!validationResult.isValid) {
                call.application.environment.log.warn("Validation failed for API key $apiKey: ${validationResult.errors}")
                return@post call.respond(HttpStatusCode.BadRequest,
                    ApiError.validationFailed(validationResult.errors))
            }

            val sanitizedPath = InputValidator.sanitize(payload.path)
            val sanitizedReferrer = payload.referrer?.let { InputValidator.sanitize(it) }
            val sanitizedSessionId = InputValidator.sanitize(payload.sessionId)
            val sanitizedEventName = payload.eventName?.let { InputValidator.sanitize(it) }

            val project = transaction {
                Projects.selectAll().where { Projects.apiKey eq apiKey }.singleOrNull()
            } ?: return@post call.respond(HttpStatusCode.NotFound,
                ApiError.notFound("Invalid API key"))

            val ua = call.request.headers["User-Agent"] ?: "unknown"
            val vHash = AnalyticsSecurity.generateVisitorHash(ip, ua, project[Projects.id].toString())

            // Apply privacy mode: restrict data collection based on level
            val (countryName, cityName) = when (privacyMode) {
                PrivacyMode.PARANOID -> null to null
                PrivacyMode.STRICT -> {
                    val (country, _) = GeoLocationService.lookup(ip)
                    country to null // No city in strict mode
                }
                PrivacyMode.STANDARD -> GeoLocationService.lookup(ip)
            }

            val browser = if (privacyMode == PrivacyMode.PARANOID) null else UserAgentParser.parseBrowser(ua)
            val os = if (privacyMode == PrivacyMode.PARANOID) null else UserAgentParser.parseOS(ua)
            val device = if (privacyMode == PrivacyMode.PARANOID) null else UserAgentParser.parseDevice(ua)

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
                // Invalidate cache for this project
                QueryCache.invalidateProject(project[Projects.id].toString())
            } catch (e: org.jetbrains.exposed.exceptions.ExposedSQLException) {
                call.application.environment.log.error("Database SQL error: ${e.message}", e)
                return@post call.respond(HttpStatusCode.InternalServerError,
                    ApiError.internalError("Failed to save event - database error"))
            } catch (e: java.sql.SQLException) {
                call.application.environment.log.error("Database connection error: ${e.message}", e)
                return@post call.respond(HttpStatusCode.InternalServerError,
                    ApiError.internalError("Failed to save event - database unavailable"))
            }

            call.respond(HttpStatusCode.Accepted)
        }
    }
}

fun Route.adminRoutes(privacyMode: PrivacyMode) {
    route("/admin") {

        // List all projects (with pagination)
        get("/projects") {
            val page = call.request.queryParameters["page"]?.toIntOrNull()
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()

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

            // If pagination params provided, return paginated response
            if (page != null && limit != null) {
                val total = allProjects.size.toLong()
                val start = (page * limit).coerceAtMost(allProjects.size)
                val end = ((page + 1) * limit).coerceAtMost(allProjects.size)
                val paged = allProjects.subList(start, end)
                call.respond(mapOf(
                    "data" to paged,
                    "total" to total,
                    "page" to page,
                    "limit" to limit,
                    "totalPages" to ((total + limit - 1) / limit)
                ))
            } else {
                // Backward compatible: return flat array
                call.respond(allProjects)
            }
        }

        // Create a new project
        post("/projects") {
            val params = call.receive<Map<String, String>>()
            val newName = params["name"] ?: return@post call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Project name is required"))
            val newDomain = params["domain"] ?: return@post call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Project domain is required"))

            // Accept client-generated API key if valid (32-char hex), otherwise generate one
            val clientKey = params["apiKey"]
            val resolvedApiKey = if (clientKey != null && clientKey.matches(Regex("^[0-9a-f]{32}$"))) {
                clientKey
            } else {
                java.util.UUID.randomUUID().toString().replace("-", "")
            }

            // Normalize domain: strip protocol, www prefix, trailing slashes
            val normalizedDomain = newDomain
                .removePrefix("https://").removePrefix("http://")
                .removePrefix("www.")
                .trimEnd('/')

            transaction {
                Projects.insert {
                    it[id] = java.util.UUID.randomUUID()
                    it[name] = newName
                    it[domain] = normalizedDomain
                    it[apiKey] = resolvedApiKey
                }
            }
            call.respond(HttpStatusCode.Created)
        }

        // Update a project
        post("/projects/{id}") {
            val uuid = safeParseUUID(call.parameters["id"])
                ?: return@post call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid or missing project ID"))

            val updates = call.receive<Map<String, String>>()
            val newName = updates["name"]?.trim()

            if (newName.isNullOrBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Project name is required"))
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
                ?: return@delete call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid or missing project ID"))

            transaction {
                val funnelIds = Funnels.selectAll().where { Funnels.projectId eq uuid }
                    .map { it[Funnels.id] }
                if (funnelIds.isNotEmpty()) {
                    FunnelSteps.deleteWhere { FunnelSteps.funnelId inList funnelIds }
                }
                Funnels.deleteWhere { Funnels.projectId eq uuid }
                ConversionGoals.deleteWhere { ConversionGoals.projectId eq uuid }
                Segments.deleteWhere { Segments.projectId eq uuid }
                Events.deleteWhere { Events.projectId eq uuid }
                Projects.deleteWhere { Projects.id eq uuid }
            }
            QueryCache.invalidateProject(uuid.toString())
            call.respond(HttpStatusCode.NoContent)
        }

        // Get stats for a specific project
        get("/projects/{id}/stats") {
            val pid = safeParseUUID(call.parameters["id"])
                ?: return@get call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid or missing project ID"))

            val stats = QueryCache.getOrCompute("$pid:stats") {
                transaction {
                    val totalViews = Events.selectAll().where { Events.projectId eq pid }.count()
                    val uniqueVisitors = Events.select(Events.visitorHash)
                        .where { Events.projectId eq pid }
                        .withDistinct()
                        .count()
                    val topPages = Events.select(Events.path, Events.path.count())
                        .where { Events.projectId eq pid }
                        .groupBy(Events.path)
                        .orderBy(Events.path.count(), SortOrder.DESC)
                        .limit(5)
                        .map { TopPage(path = it[Events.path], count = it[Events.path.count()]) }

                    ProjectStats(totalViews = totalViews, uniqueVisitors = uniqueVisitors, topPages = topPages)
                }
            }
            call.respond(stats)
        }

        // Get live map for a specific project
        get("/projects/{id}/live") {
            val id = safeParseUUID(call.parameters["id"])
                ?: return@get call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid or missing project ID"))
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
                ?: return@post call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid or missing project ID"))
            val request = call.receive<Map<String, Int>>()
            val count = request["count"] ?: 500
            val timeScope = request["timeScope"] ?: 30

            if (count < 0 || count > 3000) {
                call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Count must be between 0 and 3000"))
                return@post
            }

            val generated = transaction {
                val eventCount = generateDemoData(id, count, timeScope)
                seedDemoGoalsFunnelsSegments(id)
                eventCount
            }
            QueryCache.invalidateProject(id.toString())

            call.respond(mapOf("generated" to generated))
        }

        // Report endpoint with caching
        get("/projects/{id}/report") {
            val id = safeParseUUID(call.parameters["id"])
                ?: return@get call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid or missing project ID"))
            val filter = call.request.queryParameters["filter"] ?: "7d"

            val report = QueryCache.getOrCompute("$id:report:$filter") {
                val (start, end) = getCurrentPeriod(filter)
                generateReport(id, start, end)
            }
            call.respond(report)
        }

        // Comparison endpoint with caching
        get("/projects/{id}/report/comparison") {
            val id = safeParseUUID(call.parameters["id"])
                ?: return@get call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid or missing project ID"))
            val filter = call.request.queryParameters["filter"] ?: "7d"

            val comparisonReport = QueryCache.getOrCompute("$id:comparison:$filter") {
                val (currentStart, currentEnd) = getCurrentPeriod(filter)
                val (previousStart, previousEnd) = getPreviousPeriod(filter)

                val current = generateReport(id, currentStart, currentEnd)
                val previous = generateReport(id, previousStart, previousEnd)
                val timeSeries = generateTimeSeries(id, currentStart, currentEnd, filter)

                ComparisonReport(current = current, previous = previous, timeSeries = timeSeries)
            }
            call.respond(comparisonReport)
        }

        // Contribution calendar endpoint
        get("/projects/{id}/calendar") {
            val id = safeParseUUID(call.parameters["id"])
                ?: return@get call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid or missing project ID"))

            val calendar = QueryCache.getOrCompute("$id:calendar") {
                generateContributionCalendar(id)
            }
            call.respond(calendar)
        }

        // Raw events viewer with pagination and filtering
        get("/projects/{id}/events") {
            val id = safeParseUUID(call.parameters["id"])
                ?: return@get call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid project ID"))

            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
            val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 50).coerceIn(1, 1000)
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

        get("/projects/{id}/goals") {
            val pid = safeParseUUID(call.parameters["id"])
                ?: return@get call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid or missing project ID"))

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

        post("/projects/{id}/goals") {
            val pid = safeParseUUID(call.parameters["id"])
                ?: return@post call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid or missing project ID"))

            val request = try {
                call.receive<GoalRequest>()
            } catch (e: Exception) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid request body"))
            }

            if (request.name.isBlank() || request.matchValue.isBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Name and match value are required"))
            }

            if (request.goalType !in listOf("url", "event")) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Goal type must be 'url' or 'event'"))
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

        put("/projects/{id}/goals/{goalId}") {
            val pid = safeParseUUID(call.parameters["id"])
                ?: return@put call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid or missing project ID"))
            val goalId = safeParseUUID(call.parameters["goalId"])
                ?: return@put call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid or missing goal ID"))

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

        delete("/projects/{id}/goals/{goalId}") {
            val pid = safeParseUUID(call.parameters["id"])
                ?: return@delete call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid or missing project ID"))
            val goalId = safeParseUUID(call.parameters["goalId"])
                ?: return@delete call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid or missing goal ID"))

            transaction {
                ConversionGoals.deleteWhere {
                    (ConversionGoals.id eq goalId) and (ConversionGoals.projectId eq pid)
                }
            }
            call.respond(HttpStatusCode.NoContent)
        }

        get("/projects/{id}/goals/stats") {
            val pid = safeParseUUID(call.parameters["id"])
                ?: return@get call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid or missing project ID"))
            val filter = call.request.queryParameters["filter"] ?: "7d"

            val stats = QueryCache.getOrCompute("$pid:goalstats:$filter") {
                calculateGoalStats(pid, filter)
            }
            call.respond(stats)
        }

        // ── Funnels ──────────────────────────────────────────────────

        get("/projects/{id}/funnels") {
            val pid = safeParseUUID(call.parameters["id"])
                ?: return@get call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid or missing project ID"))

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

        post("/projects/{id}/funnels") {
            val pid = safeParseUUID(call.parameters["id"])
                ?: return@post call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid or missing project ID"))

            val request = try {
                call.receive<FunnelRequest>()
            } catch (e: Exception) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid request body"))
            }

            if (request.name.isBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Funnel name is required"))
            }

            if (request.steps.size < 2) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Funnel must have at least 2 steps"))
            }

            for (step in request.steps) {
                if (step.stepType !in listOf("url", "event")) {
                    return@post call.respond(HttpStatusCode.BadRequest,
                        ApiError.badRequest("Step type must be 'url' or 'event'"))
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

        delete("/projects/{id}/funnels/{funnelId}") {
            val pid = safeParseUUID(call.parameters["id"])
                ?: return@delete call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid or missing project ID"))
            val funnelId = safeParseUUID(call.parameters["funnelId"])
                ?: return@delete call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid or missing funnel ID"))

            transaction {
                FunnelSteps.deleteWhere { FunnelSteps.funnelId eq funnelId }
                Funnels.deleteWhere {
                    (Funnels.id eq funnelId) and (Funnels.projectId eq pid)
                }
            }
            call.respond(HttpStatusCode.NoContent)
        }

        get("/projects/{id}/funnels/{funnelId}/analysis") {
            val pid = safeParseUUID(call.parameters["id"])
                ?: return@get call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid or missing project ID"))
            val funnelId = safeParseUUID(call.parameters["funnelId"])
                ?: return@get call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid or missing funnel ID"))
            val filter = call.request.queryParameters["filter"] ?: "7d"

            val (start, end) = getCurrentPeriod(filter)
            val analysis = analyzeFunnel(funnelId, pid, start, end)
            call.respond(analysis)
        }

        // ── User Segments ─────────────────────────────────────────────

        get("/projects/{id}/segments") {
            val pid = safeParseUUID(call.parameters["id"])
                ?: return@get call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid or missing project ID"))

            val segments = transaction {
                Segments.selectAll().where { Segments.projectId eq pid }
                    .orderBy(Segments.createdAt, SortOrder.DESC)
                    .map {
                        SegmentResponse(
                            id = it[Segments.id].toString(),
                            name = it[Segments.name],
                            description = it[Segments.description],
                            filters = try {
                                Json.decodeFromString<List<SegmentFilter>>(it[Segments.filtersJson])
                            } catch (e: Exception) { emptyList() },
                            createdAt = it[Segments.createdAt].toString()
                        )
                    }
            }
            call.respond(segments)
        }

        post("/projects/{id}/segments") {
            val pid = safeParseUUID(call.parameters["id"])
                ?: return@post call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid or missing project ID"))

            val request = try {
                call.receive<SegmentRequest>()
            } catch (e: Exception) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid request body"))
            }

            if (request.name.isBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Segment name is required"))
            }

            if (request.filters.isEmpty()) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("At least one filter is required"))
            }

            val validFields = setOf("browser", "os", "device", "country", "city", "path", "referrer", "eventType")
            val validOperators = setOf("equals", "not_equals", "contains", "starts_with")
            for (filter in request.filters) {
                if (filter.field !in validFields) {
                    return@post call.respond(HttpStatusCode.BadRequest,
                        ApiError.badRequest("Invalid filter field: ${filter.field}. Valid: $validFields"))
                }
                if (filter.operator !in validOperators) {
                    return@post call.respond(HttpStatusCode.BadRequest,
                        ApiError.badRequest("Invalid operator: ${filter.operator}. Valid: $validOperators"))
                }
            }

            val segmentId = UUID.randomUUID()
            transaction {
                Segments.insert {
                    it[id] = segmentId
                    it[projectId] = pid
                    it[name] = request.name.trim()
                    it[description] = request.description?.trim()
                    it[filtersJson] = Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(SegmentFilter.serializer()), request.filters)
                }
            }

            call.respond(HttpStatusCode.Created, buildJsonObject {
                put("id", segmentId.toString())
                put("success", true)
            })
        }

        delete("/projects/{id}/segments/{segmentId}") {
            val pid = safeParseUUID(call.parameters["id"])
                ?: return@delete call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid or missing project ID"))
            val segmentId = safeParseUUID(call.parameters["segmentId"])
                ?: return@delete call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid or missing segment ID"))

            transaction {
                Segments.deleteWhere {
                    (Segments.id eq segmentId) and (Segments.projectId eq pid)
                }
            }
            call.respond(HttpStatusCode.NoContent)
        }

        get("/projects/{id}/segments/{segmentId}/analysis") {
            val pid = safeParseUUID(call.parameters["id"])
                ?: return@get call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid or missing project ID"))
            val segmentId = safeParseUUID(call.parameters["segmentId"])
                ?: return@get call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid or missing segment ID"))
            val filter = call.request.queryParameters["filter"] ?: "7d"

            val segment = transaction {
                Segments.selectAll().where {
                    (Segments.id eq segmentId) and (Segments.projectId eq pid)
                }.singleOrNull()
            } ?: return@get call.respond(HttpStatusCode.NotFound,
                ApiError.notFound("Segment not found"))

            val filters = try {
                Json.decodeFromString<List<SegmentFilter>>(segment[Segments.filtersJson])
            } catch (e: Exception) { emptyList() }

            val (start, end) = getCurrentPeriod(filter)

            val analysis = transaction {
                val allEvents = Events.selectAll().where {
                    (Events.projectId eq pid) and (Events.timestamp greaterEq start) and (Events.timestamp lessEq end)
                }.toList()

                // Apply segment filters
                val filtered = allEvents.filter { event ->
                    applySegmentFilters(event, filters)
                }

                val sessions = filtered.groupBy { it[Events.sessionId] }
                val bouncedSessions = sessions.count { (_, sessionEvents) ->
                    val uniquePages = sessionEvents.map { it[Events.path] }.distinct().size
                    val hasHeartbeat = sessionEvents.any { it[Events.eventType] == "heartbeat" }
                    uniquePages == 1 && !hasHeartbeat
                }
                val bounceRate = if (sessions.isNotEmpty()) (bouncedSessions.toDouble() / sessions.size) * 100.0 else 0.0

                val topPages = filtered.groupBy { it[Events.path] }
                    .map { (path, events) -> StatEntry(path, events.size.toLong()) }
                    .sortedByDescending { it.value }
                    .take(10)

                SegmentAnalysis(
                    segmentId = segmentId.toString(),
                    segmentName = segment[Segments.name],
                    totalViews = filtered.size.toLong(),
                    uniqueVisitors = filtered.map { it[Events.visitorHash] }.distinct().size.toLong(),
                    bounceRate = bounceRate,
                    topPages = topPages,
                    matchingEvents = filtered.size.toLong()
                )
            }
            call.respond(analysis)
        }
    }
}

/**
 * Apply segment filters to an event row
 */
private fun applySegmentFilters(event: org.jetbrains.exposed.sql.ResultRow, filters: List<SegmentFilter>): Boolean {
    if (filters.isEmpty()) return true

    var result = matchesFilter(event, filters[0])

    for (i in 1 until filters.size) {
        val logic = filters[i - 1].logic.uppercase()
        val matches = matchesFilter(event, filters[i])

        result = if (logic == "OR") result || matches else result && matches
    }

    return result
}

private fun matchesFilter(event: org.jetbrains.exposed.sql.ResultRow, filter: SegmentFilter): Boolean {
    val fieldValue = when (filter.field) {
        "browser" -> event[Events.browser]
        "os" -> event[Events.os]
        "device" -> event[Events.device]
        "country" -> event[Events.country]
        "city" -> event[Events.city]
        "path" -> event[Events.path]
        "referrer" -> event[Events.referrer]
        "eventType" -> event[Events.eventType]
        else -> null
    } ?: return false

    return when (filter.operator) {
        "equals" -> fieldValue.equals(filter.value, ignoreCase = true)
        "not_equals" -> !fieldValue.equals(filter.value, ignoreCase = true)
        "contains" -> fieldValue.contains(filter.value, ignoreCase = true)
        "starts_with" -> fieldValue.startsWith(filter.value, ignoreCase = true)
        else -> false
    }
}

/**
 * Generate realistic demo/dummy data for testing
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
        null, "https://google.com/search", "https://twitter.com",
        "https://facebook.com", "https://github.com", "https://reddit.com",
        "https://linkedin.com", "https://news.ycombinator.com"
    )

    val customEventNames = listOf(
        "signup", "download", "purchase", "newsletter_subscribe", "share",
        "contact_form", "add_to_cart", "video_play", "search", "scroll_depth"
    )
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

        val isBounce = random.nextDouble() < 0.4
        val sessionEvents = if (isBounce) 1 else minOf(2 + random.nextInt(4), remaining)

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
            } else if (!isBounce && random.nextDouble() < 0.25) {
                eventType = "custom"
                path = firstPath
                customEventName = customEventNames.random()
            } else if (!isBounce && random.nextDouble() < 0.4) {
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

/**
 * Seed demo conversion goals, funnels, and segments for a project.
 * Skips creation if goals/funnels/segments already exist for the project.
 */
private fun seedDemoGoalsFunnelsSegments(projectId: java.util.UUID) {
    // Only seed if none exist yet
    val hasGoals = ConversionGoals.selectAll().where { ConversionGoals.projectId eq projectId }.count() > 0
    val hasFunnels = Funnels.selectAll().where { Funnels.projectId eq projectId }.count() > 0
    val hasSegments = Segments.selectAll().where { Segments.projectId eq projectId }.count() > 0

    if (!hasGoals) {
        // Conversion goals matching the demo event names and paths
        val goals = listOf(
            Triple("Signups", "event", "signup"),
            Triple("Purchases", "event", "purchase"),
            Triple("Newsletter subscribers", "event", "newsletter_subscribe"),
            Triple("Pricing page visits", "url", "/pricing"),
            Triple("Blog readers", "url", "/blog"),
        )
        for ((name, type, matchValue) in goals) {
            ConversionGoals.insert {
                it[id] = UUID.randomUUID()
                it[ConversionGoals.projectId] = projectId
                it[ConversionGoals.name] = name
                it[goalType] = type
                it[ConversionGoals.matchValue] = matchValue
            }
        }
    }

    if (!hasFunnels) {
        // Funnel 1: Product purchase funnel
        val purchaseFunnelId = UUID.randomUUID()
        Funnels.insert {
            it[id] = purchaseFunnelId
            it[Funnels.projectId] = projectId
            it[name] = "Product purchase"
        }
        val purchaseSteps = listOf(
            Triple(1, "View products", "/products" to "url"),
            Triple(2, "View product detail", "/products/item-1" to "url"),
            Triple(3, "Add to cart", "add_to_cart" to "event"),
            Triple(4, "Complete purchase", "purchase" to "event"),
        )
        for ((order, stepName, matchPair) in purchaseSteps) {
            FunnelSteps.insert {
                it[id] = UUID.randomUUID()
                it[funnelId] = purchaseFunnelId
                it[stepNumber] = order
                it[name] = stepName
                it[stepType] = matchPair.second
                it[matchValue] = matchPair.first
            }
        }

        // Funnel 2: Signup funnel
        val signupFunnelId = UUID.randomUUID()
        Funnels.insert {
            it[id] = signupFunnelId
            it[Funnels.projectId] = projectId
            it[name] = "User signup"
        }
        val signupSteps = listOf(
            Triple(1, "Visit homepage", "/" to "url"),
            Triple(2, "View pricing", "/pricing" to "url"),
            Triple(3, "Sign up", "signup" to "event"),
        )
        for ((order, stepName, matchPair) in signupSteps) {
            FunnelSteps.insert {
                it[id] = UUID.randomUUID()
                it[funnelId] = signupFunnelId
                it[stepNumber] = order
                it[name] = stepName
                it[stepType] = matchPair.second
                it[matchValue] = matchPair.first
            }
        }
    }

    if (!hasSegments) {
        val segments = listOf(
            Triple(
                "Mobile visitors",
                "Visitors using mobile devices",
                """[{"field":"device","operator":"equals","value":"Mobile","logic":"AND"}]"""
            ),
            Triple(
                "US traffic",
                "Visitors from the United States",
                """[{"field":"country","operator":"equals","value":"United States","logic":"AND"}]"""
            ),
            Triple(
                "Chrome desktop users",
                "Desktop visitors using Chrome",
                """[{"field":"browser","operator":"equals","value":"Chrome","logic":"AND"},{"field":"device","operator":"equals","value":"Desktop","logic":"AND"}]"""
            ),
        )
        for ((name, description, filtersJson) in segments) {
            Segments.insert {
                it[id] = UUID.randomUUID()
                it[Segments.projectId] = projectId
                it[Segments.name] = name
                it[Segments.description] = description
                it[Segments.filtersJson] = filtersJson
            }
        }
    }
}
