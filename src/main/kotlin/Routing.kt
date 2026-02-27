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
import se.onemanstudio.db.RefreshTokens
import se.onemanstudio.db.Segments
import se.onemanstudio.db.Webhooks
import se.onemanstudio.db.WebhookDeliveries
import se.onemanstudio.middleware.RateLimiter
import se.onemanstudio.middleware.QueryCache
import se.onemanstudio.middleware.WidgetCache
import se.onemanstudio.middleware.models.RateLimitResult
import se.onemanstudio.api.models.*
import se.onemanstudio.api.models.dashboard.ComparisonReport
import se.onemanstudio.api.models.dashboard.TopPage
import se.onemanstudio.services.GeoLocationService
import se.onemanstudio.core.AnalyticsSecurity
import se.onemanstudio.core.ServiceManager
import se.onemanstudio.middleware.InputValidator
import se.onemanstudio.services.UserAgentParser
import se.onemanstudio.core.JwtService
import se.onemanstudio.core.models.UserRole
import se.onemanstudio.core.models.UserSession
import se.onemanstudio.core.getUserRole
import se.onemanstudio.core.verifyCredentials
import se.onemanstudio.config.ConfigLoader
import se.onemanstudio.middleware.requireRole
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

fun Application.configureRouting(config: AppConfig, rateLimiter: RateLimiter) {
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
                // Session rotation: clear any existing session before setting a new one
                call.sessions.clear<UserSession>()
                val role = getUserRole(loginRequest.username)
                call.sessions.set(UserSession(username = loginRequest.username, role = role))
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

        // ── JWT Token Endpoints ──────────────────────────────────────

        // Get JWT access token + refresh token
        post("/api/token") {
            val loginRequest = try {
                call.receive<LoginRequest>()
            } catch (e: Exception) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid request body"))
            }

            val tokenConfig = ConfigLoader.load()
            val isValid = verifyCredentials(
                loginRequest.username,
                loginRequest.password,
                tokenConfig,
                call.application.environment.log
            )

            if (!isValid) {
                return@post call.respond(HttpStatusCode.Unauthorized,
                    ApiError.unauthorized("Invalid credentials"))
            }

            val tokenRole = getUserRole(loginRequest.username)
            val accessToken = JwtService.generateAccessToken(loginRequest.username, tokenRole)
            val refreshToken = JwtService.generateRefreshToken()
            val family = UUID.randomUUID().toString()
            val tokenId = UUID.randomUUID()

            transaction {
                RefreshTokens.insert {
                    it[RefreshTokens.id] = tokenId
                    it[RefreshTokens.username] = loginRequest.username
                    it[RefreshTokens.tokenHash] = JwtService.hashToken(refreshToken)
                    it[RefreshTokens.family] = family
                    it[RefreshTokens.expiresAt] = LocalDateTime.now().plusDays(7)
                }
            }

            call.respond(HttpStatusCode.OK, buildJsonObject {
                put("accessToken", accessToken)
                put("refreshToken", refreshToken)
                put("tokenType", "Bearer")
                put("expiresIn", 900) // 15 minutes in seconds
            })
        }

        // Rotate refresh token
        post("/api/token/refresh") {
            @Serializable
            data class RefreshRequest(val refreshToken: String)

            val body = try {
                call.receive<RefreshRequest>()
            } catch (e: Exception) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Missing refreshToken"))
            }

            val tokenHash = JwtService.hashToken(body.refreshToken)

            val result = transaction {
                val existing = RefreshTokens.selectAll().where {
                    (RefreshTokens.tokenHash eq tokenHash) and (RefreshTokens.revokedAt.isNull())
                }.singleOrNull() ?: return@transaction null

                // Check expiry
                if (existing[RefreshTokens.expiresAt].isBefore(LocalDateTime.now())) {
                    return@transaction null
                }

                val tokenFamily = existing[RefreshTokens.family]
                val existingId = existing[RefreshTokens.id]
                val existingUsername = existing[RefreshTokens.username]

                // Revoke the used token
                RefreshTokens.update({ RefreshTokens.id eq existingId }) {
                    it[RefreshTokens.revokedAt] = LocalDateTime.now()
                }

                // Issue new token in the same family
                val newRefreshToken = JwtService.generateRefreshToken()
                val newTokenId = UUID.randomUUID()

                RefreshTokens.insert {
                    it[RefreshTokens.id] = newTokenId
                    it[RefreshTokens.username] = existingUsername
                    it[RefreshTokens.tokenHash] = JwtService.hashToken(newRefreshToken)
                    it[RefreshTokens.family] = tokenFamily
                    it[RefreshTokens.expiresAt] = LocalDateTime.now().plusDays(7)
                }

                // Link old token to its successor
                RefreshTokens.update({ RefreshTokens.id eq existingId }) {
                    it[RefreshTokens.replacedBy] = newTokenId
                }

                Pair(existingUsername, newRefreshToken)
            }

            if (result == null) {
                return@post call.respond(HttpStatusCode.Unauthorized,
                    ApiError.unauthorized("Invalid or expired refresh token"))
            }

            val (username, newRefreshToken) = result
            val refreshRole = getUserRole(username)
            val newAccessToken = JwtService.generateAccessToken(username, refreshRole)

            call.respond(HttpStatusCode.OK, buildJsonObject {
                put("accessToken", newAccessToken)
                put("refreshToken", newRefreshToken)
                put("tokenType", "Bearer")
                put("expiresIn", 900)
            })
        }

        // ── Password Reset ────────────────────────────────────────
        // Requires the server salt for verification (only server operator has it)
        post("/api/password-reset") {
            val body = try {
                call.receive<PasswordResetRequest>()
            } catch (e: Exception) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid request body"))
            }

            if (body.newPassword.length < 8) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Password must be at least 8 characters"))
            }

            val resetConfig = ConfigLoader.load()
            if (body.serverSalt != resetConfig.security.serverSalt) {
                return@post call.respond(HttpStatusCode.Forbidden,
                    ApiError(error = "Invalid server salt", code = "FORBIDDEN"))
            }

            val hashedPassword = org.mindrot.jbcrypt.BCrypt.hashpw(
                body.newPassword,
                org.mindrot.jbcrypt.BCrypt.gensalt(12)
            )

            // Update password in the Users table for the admin user
            transaction {
                se.onemanstudio.db.Users.update({
                    se.onemanstudio.db.Users.username eq resetConfig.security.adminUsername
                }) {
                    it[se.onemanstudio.db.Users.passwordHash] = hashedPassword
                }

                // Invalidate all refresh tokens
                RefreshTokens.deleteAll()
            }

            call.application.environment.log.info("Password reset completed for admin user")

            call.respond(HttpStatusCode.OK, buildJsonObject {
                put("success", true)
                put("message", "Password updated successfully. All sessions have been invalidated.")
            })
        }

        // Serve the tracker script
        staticResources("/tracker", "tracker")

        // Serve the Admin HTML
        staticResources("/admin-panel", "static") {
            default("admin.html")
        }

        // Admin API - protected by session auth or JWT
        authenticate("admin-session", "api-jwt") {
            adminRoutes(privacyMode, config.security.allowedOrigins, rateLimiter)
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
            val sanitizedUtmSource = payload.utmSource?.let { InputValidator.sanitize(it) }
            val sanitizedUtmMedium = payload.utmMedium?.let { InputValidator.sanitize(it) }
            val sanitizedUtmCampaign = payload.utmCampaign?.let { InputValidator.sanitize(it) }
            val sanitizedUtmTerm = payload.utmTerm?.let { InputValidator.sanitize(it) }
            val sanitizedUtmContent = payload.utmContent?.let { InputValidator.sanitize(it) }
            val sanitizedTargetUrl = payload.targetUrl?.let { InputValidator.sanitize(it) }
            val sanitizedProperties = payload.properties?.let { InputValidator.sanitize(it) }

            val project = transaction {
                Projects.selectAll().where { Projects.apiKey eq apiKey }.singleOrNull()
            } ?: return@post call.respond(HttpStatusCode.NotFound,
                ApiError.notFound("Invalid API key"))

            val ua = call.request.headers["User-Agent"] ?: "unknown"
            val vHash = AnalyticsSecurity.generateVisitorHash(ip, ua, project[Projects.id].toString())

            // Apply privacy mode: restrict data collection based on level
            val geoResult = when (privacyMode) {
                PrivacyMode.PARANOID -> se.onemanstudio.services.GeoResult()
                PrivacyMode.STRICT -> {
                    val result = GeoLocationService.lookup(ip)
                    se.onemanstudio.services.GeoResult(result.country, null, null, null, null)
                }
                PrivacyMode.STANDARD -> GeoLocationService.lookup(ip)
            }

            val browser = if (privacyMode == PrivacyMode.PARANOID) null else UserAgentParser.parseBrowser(ua)
            val os = if (privacyMode == PrivacyMode.PARANOID) null else UserAgentParser.parseOS(ua)
            val device = if (privacyMode == PrivacyMode.PARANOID) null else UserAgentParser.parseDevice(ua)

            // Validate coordinate bounds (defensive)
            val safeLat = geoResult.latitude?.takeIf { it in -90.0..90.0 }
            val safeLon = geoResult.longitude?.takeIf { it in -180.0..180.0 }

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
                        it[country] = geoResult.country
                        it[city] = geoResult.city
                        it[Events.browser] = browser
                        it[Events.os] = os
                        it[Events.device] = device
                        it[utmSource] = sanitizedUtmSource
                        it[utmMedium] = sanitizedUtmMedium
                        it[utmCampaign] = sanitizedUtmCampaign
                        it[utmTerm] = sanitizedUtmTerm
                        it[utmContent] = sanitizedUtmContent
                        it[scrollDepth] = payload.scrollDepth
                        it[region] = geoResult.region
                        it[targetUrl] = sanitizedTargetUrl
                        it[properties] = sanitizedProperties
                        it[latitude] = safeLat
                        it[longitude] = safeLon
                    }
                }
                // Invalidate cache for this project
                QueryCache.invalidateProject(project[Projects.id].toString())
                WidgetCache.invalidateProject(project[Projects.id].toString())

                // Fire webhooks for matching events (non-blocking)
                try {
                    se.onemanstudio.services.WebhookTrigger.checkAndFire(
                        projectId = project[Projects.id],
                        eventType = payload.type,
                        eventName = sanitizedEventName,
                        path = sanitizedPath,
                        properties = sanitizedProperties
                    )
                } catch (e: Exception) {
                    call.application.environment.log.warn("Webhook trigger error: ${e.message}")
                }
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

fun Route.adminRoutes(privacyMode: PrivacyMode, allowedOrigins: List<String>, rateLimiter: RateLimiter) {
    route("/admin") {

        // Admin-specific security intercepts
        intercept(ApplicationCallPipeline.Plugins) {
            // CORS origin guard: validate Origin header against allowlist
            if (!se.onemanstudio.middleware.AdminCorsGuard.check(call, allowedOrigins)) {
                finish()
                return@intercept
            }

            // Rate limiting for admin routes (200 req/min per IP)
            val ip = call.request.origin.remoteHost
            val adminLimit = 200
            val result = rateLimiter.checkRateLimit(ip, "admin-panel")
            if (result is RateLimitResult.Exceeded) {
                call.respond(
                    HttpStatusCode.TooManyRequests,
                    ApiError.rateLimited("Too many requests", result.limitType, result.limit, result.window)
                )
                finish()
                return@intercept
            }
        }

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
            if (!call.requireRole(UserRole.ADMIN)) return@post
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
            if (!call.requireRole(UserRole.ADMIN)) return@delete
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
            WidgetCache.invalidateProject(uuid.toString())
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
                    .map { VisitSnippet(it[Events.path], it[Events.timestamp].toString(), it[Events.city], it[Events.country]) }
            }
            call.respond(liveData)
        }

        // Real-time visitor count (distinct visitors in last 5 minutes)
        get("/projects/{id}/realtime-count") {
            val id = safeParseUUID(call.parameters["id"])
                ?: return@get call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid or missing project ID"))
            val fiveMinutesAgo = LocalDateTime.now().minusMinutes(5)

            val count = transaction {
                Events.select(Events.visitorHash)
                    .where {
                        (Events.projectId eq id) and
                        (Events.timestamp greaterEq fiveMinutesAgo)
                    }
                    .withDistinct()
                    .count()
            }
            call.respond(buildJsonObject {
                put("activeVisitors", count)
                put("timestamp", LocalDateTime.now().toString())
            })
        }

        // Globe visualization endpoint — aggregated visitor locations with coordinates
        get("/projects/{id}/globe") {
            val id = safeParseUUID(call.parameters["id"])
                ?: return@get call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid or missing project ID"))

            val range = call.request.queryParameters["range"] ?: "realtime"
            val cutoff = when (range) {
                "1m" -> LocalDateTime.now().minusMinutes(1)
                "1h" -> LocalDateTime.now().minusHours(1)
                "1d" -> LocalDateTime.now().minusDays(1)
                else -> LocalDateTime.now().minusMinutes(5) // realtime = last 5 min
            }

            val globeData = if (range == "realtime") {
                computeGlobeData(id, cutoff)
            } else {
                val cacheKey = "$id:globe:$range"
                QueryCache.getOrCompute(cacheKey) {
                    computeGlobeData(id, cutoff)
                }
            }

            call.respond(globeData)
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
            WidgetCache.invalidateProject(id.toString())

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
                            duration = row[Events.duration],
                            utmSource = row[Events.utmSource],
                            utmCampaign = row[Events.utmCampaign],
                            scrollDepth = row[Events.scrollDepth],
                            region = row[Events.region],
                            targetUrl = row[Events.targetUrl],
                            properties = row[Events.properties],
                            latitude = row[Events.latitude],
                            longitude = row[Events.longitude]
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
            if (!call.requireRole(UserRole.ADMIN)) return@post
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
            if (!call.requireRole(UserRole.ADMIN)) return@put
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
            if (!call.requireRole(UserRole.ADMIN)) return@delete
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
            if (!call.requireRole(UserRole.ADMIN)) return@post
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
            if (!call.requireRole(UserRole.ADMIN)) return@delete
            val pid = safeParseUUID(call.parameters["id"])
                ?: return@delete call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid or missing project ID"))
            val funnelId = safeParseUUID(call.parameters["funnelId"])
                ?: return@delete call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid or missing funnel ID"))

            val deleted = transaction {
                // Verify funnel belongs to the project before deleting steps
                val funnelExists = Funnels.selectAll().where {
                    (Funnels.id eq funnelId) and (Funnels.projectId eq pid)
                }.count() > 0

                if (funnelExists) {
                    FunnelSteps.deleteWhere { FunnelSteps.funnelId eq funnelId }
                    Funnels.deleteWhere {
                        (Funnels.id eq funnelId) and (Funnels.projectId eq pid)
                    }
                }
                funnelExists
            }

            if (deleted) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound,
                    ApiError.notFound("Funnel not found"))
            }
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
            val analysis = try {
                analyzeFunnel(funnelId, pid, start, end)
            } catch (e: IllegalArgumentException) {
                return@get call.respond(HttpStatusCode.NotFound,
                    ApiError.notFound("Funnel not found"))
            }
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
            if (!call.requireRole(UserRole.ADMIN)) return@post
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
            if (!call.requireRole(UserRole.ADMIN)) return@delete
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

        // ── Webhooks ─────────────────────────────────────────────────

        get("/projects/{id}/webhooks") {
            val pid = safeParseUUID(call.parameters["id"])
                ?: return@get call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid or missing project ID"))

            val webhooks = transaction {
                Webhooks.selectAll().where { Webhooks.projectId eq pid }.map {
                    WebhookResponse(
                        id = it[Webhooks.id].toString(),
                        projectId = it[Webhooks.projectId].toString(),
                        url = it[Webhooks.url],
                        events = it[Webhooks.events].split(",").map { e -> e.trim() },
                        isActive = it[Webhooks.isActive],
                        createdAt = it[Webhooks.createdAt].toString()
                    )
                }
            }
            call.respond(webhooks)
        }

        post("/projects/{id}/webhooks") {
            if (!call.requireRole(UserRole.ADMIN)) return@post
            val pid = safeParseUUID(call.parameters["id"])
                ?: return@post call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid or missing project ID"))

            val request = try {
                call.receive<CreateWebhookRequest>()
            } catch (e: Exception) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid request body"))
            }

            if (request.url.isBlank() || !request.url.startsWith("https://")) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Webhook URL must use HTTPS"))
            }

            val webhookId = UUID.randomUUID()
            // Auto-generate a cryptographically secure HMAC secret
            val secret = java.security.SecureRandom().let { rng ->
                val bytes = ByteArray(32)
                rng.nextBytes(bytes)
                bytes.joinToString("") { "%02x".format(it) }
            }

            transaction {
                Webhooks.insert {
                    it[Webhooks.id] = webhookId
                    it[Webhooks.projectId] = pid
                    it[Webhooks.url] = request.url
                    it[Webhooks.secret] = secret
                    it[Webhooks.events] = request.events.joinToString(",")
                }
            }

            se.onemanstudio.services.WebhookTrigger.invalidateProject(pid.toString())

            call.respond(HttpStatusCode.Created, buildJsonObject {
                put("id", webhookId.toString())
                put("secret", secret) // Only shown once at creation time
                put("success", true)
            })
        }

        delete("/projects/{id}/webhooks/{webhookId}") {
            if (!call.requireRole(UserRole.ADMIN)) return@delete
            val pid = safeParseUUID(call.parameters["id"])
                ?: return@delete call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid or missing project ID"))
            val webhookId = safeParseUUID(call.parameters["webhookId"])
                ?: return@delete call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid or missing webhook ID"))

            val deleted = transaction {
                // Verify webhook belongs to project
                val exists = Webhooks.selectAll().where {
                    (Webhooks.id eq webhookId) and (Webhooks.projectId eq pid)
                }.count() > 0

                if (exists) {
                    WebhookDeliveries.deleteWhere { WebhookDeliveries.webhookId eq webhookId }
                    Webhooks.deleteWhere {
                        (Webhooks.id eq webhookId) and (Webhooks.projectId eq pid)
                    }
                }
                exists
            }

            if (deleted) {
                se.onemanstudio.services.WebhookTrigger.invalidateProject(pid.toString())
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound, ApiError.notFound("Webhook not found"))
            }
        }

        get("/projects/{id}/webhooks/{webhookId}/deliveries") {
            val pid = safeParseUUID(call.parameters["id"])
                ?: return@get call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid or missing project ID"))
            val webhookId = safeParseUUID(call.parameters["webhookId"])
                ?: return@get call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid or missing webhook ID"))

            // Verify webhook belongs to project
            val webhookExists = transaction {
                Webhooks.selectAll().where {
                    (Webhooks.id eq webhookId) and (Webhooks.projectId eq pid)
                }.count() > 0
            }
            if (!webhookExists) {
                return@get call.respond(HttpStatusCode.NotFound,
                    ApiError.notFound("Webhook not found"))
            }

            val deliveries = transaction {
                WebhookDeliveries.selectAll()
                    .where { WebhookDeliveries.webhookId eq webhookId }
                    .orderBy(WebhookDeliveries.createdAt, SortOrder.DESC)
                    .limit(50)
                    .map {
                        WebhookDeliveryResponse(
                            id = it[WebhookDeliveries.id].toString(),
                            eventType = it[WebhookDeliveries.eventType],
                            responseCode = it[WebhookDeliveries.responseCode],
                            attempt = it[WebhookDeliveries.attempt],
                            status = it[WebhookDeliveries.status],
                            createdAt = it[WebhookDeliveries.createdAt].toString(),
                            deliveredAt = it[WebhookDeliveries.deliveredAt]?.toString()
                        )
                    }
            }
            call.respond(deliveries)
        }

        post("/projects/{id}/webhooks/{webhookId}/test") {
            if (!call.requireRole(UserRole.ADMIN)) return@post
            val pid = safeParseUUID(call.parameters["id"])
                ?: return@post call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid or missing project ID"))
            val webhookId = safeParseUUID(call.parameters["webhookId"])
                ?: return@post call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid or missing webhook ID"))

            val webhook = transaction {
                Webhooks.selectAll().where {
                    (Webhooks.id eq webhookId) and (Webhooks.projectId eq pid)
                }.singleOrNull()
            } ?: return@post call.respond(HttpStatusCode.NotFound,
                ApiError.notFound("Webhook not found"))

            val testPayload = buildJsonObject {
                put("event", "test")
                put("projectId", pid.toString())
                put("message", "This is a test webhook delivery from Mini Numbers")
                put("timestamp", LocalDateTime.now().toString())
            }.toString()

            se.onemanstudio.services.WebhookService.deliverAsync(
                webhookId = webhookId,
                url = webhook[Webhooks.url],
                secret = webhook[Webhooks.secret],
                eventType = "test",
                payload = testPayload
            )

            call.respond(HttpStatusCode.OK, buildJsonObject {
                put("success", true)
                put("message", "Test webhook queued for delivery")
            })
        }

        // ── User Management (admin-only) ─────────────────────────────

        get("/users") {
            if (!call.requireRole(UserRole.ADMIN)) return@get
            val users = transaction {
                se.onemanstudio.db.Users.selectAll().map {
                    UserResponse(
                        id = it[se.onemanstudio.db.Users.id].toString(),
                        username = it[se.onemanstudio.db.Users.username],
                        role = it[se.onemanstudio.db.Users.role],
                        isActive = it[se.onemanstudio.db.Users.isActive],
                        createdAt = it[se.onemanstudio.db.Users.createdAt].toString()
                    )
                }
            }
            call.respond(users)
        }

        post("/users") {
            if (!call.requireRole(UserRole.ADMIN)) return@post
            val request = try {
                call.receive<CreateUserRequest>()
            } catch (e: Exception) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid request body"))
            }

            if (request.username.isBlank() || request.username.length > 100) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Username must be 1-100 characters"))
            }
            if (request.password.length < 8) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Password must be at least 8 characters"))
            }
            if (request.role !in listOf("admin", "viewer")) {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Role must be 'admin' or 'viewer'"))
            }

            // Check for duplicate username
            val exists = transaction {
                se.onemanstudio.db.Users.selectAll().where {
                    se.onemanstudio.db.Users.username eq request.username
                }.count() > 0
            }
            if (exists) {
                return@post call.respond(HttpStatusCode.Conflict,
                    ApiError(error = "Username already exists", code = "CONFLICT"))
            }

            val userId = UUID.randomUUID()
            val hashedPassword = org.mindrot.jbcrypt.BCrypt.hashpw(
                request.password,
                org.mindrot.jbcrypt.BCrypt.gensalt(12)
            )

            transaction {
                se.onemanstudio.db.Users.insert {
                    it[se.onemanstudio.db.Users.id] = userId
                    it[se.onemanstudio.db.Users.username] = request.username
                    it[se.onemanstudio.db.Users.passwordHash] = hashedPassword
                    it[se.onemanstudio.db.Users.role] = request.role
                }
            }

            call.respond(HttpStatusCode.Created, buildJsonObject {
                put("id", userId.toString())
                put("success", true)
            })
        }

        put("/users/{userId}/role") {
            if (!call.requireRole(UserRole.ADMIN)) return@put
            val userId = safeParseUUID(call.parameters["userId"])
                ?: return@put call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid or missing user ID"))

            val request = try {
                call.receive<UpdateUserRoleRequest>()
            } catch (e: Exception) {
                return@put call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid request body"))
            }

            if (request.role !in listOf("admin", "viewer")) {
                return@put call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Role must be 'admin' or 'viewer'"))
            }

            val updated = transaction {
                se.onemanstudio.db.Users.update({
                    se.onemanstudio.db.Users.id eq userId
                }) {
                    it[se.onemanstudio.db.Users.role] = request.role
                }
            }

            if (updated > 0) {
                call.respond(HttpStatusCode.OK, buildJsonObject { put("success", true) })
            } else {
                call.respond(HttpStatusCode.NotFound, ApiError.notFound("User not found"))
            }
        }

        delete("/users/{userId}") {
            if (!call.requireRole(UserRole.ADMIN)) return@delete
            val userId = safeParseUUID(call.parameters["userId"])
                ?: return@delete call.respond(HttpStatusCode.BadRequest,
                    ApiError.badRequest("Invalid or missing user ID"))

            val deleted = transaction {
                se.onemanstudio.db.Users.deleteWhere {
                    se.onemanstudio.db.Users.id eq userId
                }
            }

            if (deleted > 0) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound, ApiError.notFound("User not found"))
            }
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
 * Compute aggregated globe visualization data.
 * Groups visitors by rounded lat/lon (1 decimal place, ~11km) for clustering.
 */
private fun computeGlobeData(projectId: UUID, cutoff: LocalDateTime): GlobeData {
    return transaction {
        val events = Events.selectAll().where {
            (Events.projectId eq projectId) and
            (Events.timestamp greaterEq cutoff) and
            (Events.latitude.isNotNull()) and
            (Events.longitude.isNotNull())
        }.toList()

        // Round coordinates to 1 decimal place for clustering
        data class GeoKey(val lat: Double, val lng: Double)

        val grouped = events.groupBy { row ->
            GeoKey(
                Math.round(row[Events.latitude]!! * 10.0) / 10.0,
                Math.round(row[Events.longitude]!! * 10.0) / 10.0
            )
        }

        val visitors = grouped.map { (key, rows) ->
            val mostRecent = rows.maxByOrNull { it[Events.timestamp] }
            GlobeVisitor(
                lat = key.lat,
                lng = key.lng,
                city = mostRecent?.get(Events.city),
                country = mostRecent?.get(Events.country),
                count = rows.size.toLong(),
                lastSeen = (mostRecent?.get(Events.timestamp) ?: LocalDateTime.now()).toString()
            )
        }.sortedByDescending { it.count }

        val totalActive = events.map { it[Events.visitorHash] }.distinct().size.toLong()

        GlobeData(visitors = visitors, totalActive = totalActive)
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
    val regions = mapOf(
        "United States" to listOf("California", "New York", "Texas", "Florida", "Illinois"),
        "United Kingdom" to listOf("England", "Scotland", "Wales"),
        "Canada" to listOf("Ontario", "Quebec", "British Columbia", "Alberta"),
        "Germany" to listOf("Bavaria", "North Rhine-Westphalia", "Berlin", "Hamburg"),
        "France" to listOf("Île-de-France", "Provence-Alpes-Côte d'Azur", "Auvergne-Rhône-Alpes"),
        "Spain" to listOf("Catalonia", "Madrid", "Andalusia", "Valencia"),
        "Italy" to listOf("Lombardy", "Lazio", "Campania", "Tuscany"),
        "Australia" to listOf("New South Wales", "Victoria", "Queensland", "Western Australia"),
        "Japan" to listOf("Tokyo", "Osaka", "Kanagawa", "Aichi"),
        "Brazil" to listOf("São Paulo", "Rio de Janeiro", "Minas Gerais", "Bahia")
    )
    val cityCoordinates = mapOf(
        "New York" to (40.7128 to -74.0060), "Los Angeles" to (34.0522 to -118.2437),
        "Chicago" to (41.8781 to -87.6298), "Houston" to (29.7604 to -95.3698),
        "Phoenix" to (33.4484 to -112.0740), "London" to (51.5074 to -0.1278),
        "Manchester" to (53.4808 to -2.2426), "Birmingham" to (52.4862 to -1.8904),
        "Leeds" to (53.8008 to -1.5491), "Glasgow" to (55.8642 to -4.2518),
        "Toronto" to (43.6532 to -79.3832), "Montreal" to (45.5017 to -73.5673),
        "Vancouver" to (49.2827 to -123.1207), "Calgary" to (51.0447 to -114.0719),
        "Ottawa" to (45.4215 to -75.6972), "Berlin" to (52.5200 to 13.4050),
        "Munich" to (48.1351 to 11.5820), "Hamburg" to (53.5511 to 9.9937),
        "Frankfurt" to (50.1109 to 8.6821), "Cologne" to (50.9375 to 6.9603),
        "Paris" to (48.8566 to 2.3522), "Marseille" to (43.2965 to 5.3698),
        "Lyon" to (45.7640 to 4.8357), "Toulouse" to (43.6047 to 1.4442),
        "Nice" to (43.7102 to 7.2620), "Madrid" to (40.4168 to -3.7038),
        "Barcelona" to (41.3874 to 2.1686), "Valencia" to (39.4699 to -0.3763),
        "Seville" to (37.3891 to -5.9845), "Zaragoza" to (41.6488 to -0.8891),
        "Rome" to (41.9028 to 12.4964), "Milan" to (45.4642 to 9.1900),
        "Naples" to (40.8518 to 14.2681), "Turin" to (45.0703 to 7.6869),
        "Florence" to (43.7696 to 11.2558), "Sydney" to (-33.8688 to 151.2093),
        "Melbourne" to (-37.8136 to 144.9631), "Brisbane" to (-27.4698 to 153.0251),
        "Perth" to (-31.9505 to 115.8605), "Adelaide" to (-34.9285 to 138.6007),
        "Tokyo" to (35.6762 to 139.6503), "Osaka" to (34.6937 to 135.5023),
        "Yokohama" to (35.4437 to 139.6380), "Nagoya" to (35.1815 to 136.9066),
        "Sapporo" to (43.0618 to 141.3545), "São Paulo" to (-23.5505 to -46.6333),
        "Rio de Janeiro" to (-22.9068 to -43.1729), "Brasília" to (-15.8267 to -47.9218),
        "Salvador" to (-12.9714 to -38.5124), "Fortaleza" to (-3.7172 to -38.5433)
    )
    val utmSources = listOf(null, null, null, "google", "facebook", "twitter", "newsletter", "linkedin")
    val utmMediums = listOf(null, null, null, "cpc", "social", "email", "organic", "referral")
    val utmCampaigns = listOf(null, null, null, "spring_sale", "product_launch", "brand_awareness", "retargeting")
    val outboundUrls = listOf(
        "https://github.com/example", "https://docs.example.com", "https://twitter.com/share",
        "https://linkedin.com/post", "https://medium.com/article"
    )
    val downloadUrls = listOf(
        "https://example.com/files/report.pdf", "https://example.com/files/data.csv",
        "https://example.com/files/sdk.zip", "https://example.com/files/guide.docx"
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

            val coords = city?.let { cityCoordinates[it] }
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
                it[Events.region] = regions[country]?.random()
                it[Events.latitude] = coords?.first
                it[Events.longitude] = coords?.second
                // Add UTM data to some pageview events
                if (eventType == "pageview" && isFirstEvent) {
                    it[Events.utmSource] = utmSources.random()
                    it[Events.utmMedium] = utmMediums.random()
                    it[Events.utmCampaign] = utmCampaigns.random()
                }
                // Add properties to some custom events
                if (eventType == "custom" && random.nextDouble() < 0.5) {
                    it[Events.properties] = """{"plan":"${listOf("free","pro","enterprise").random()}","value":"${random.nextInt(100)}"}"""
                }
            }

            // Generate occasional scroll events
            if (eventType == "pageview" && random.nextDouble() < 0.6) {
                val scrollDepths = listOf(25, 50, 75, 100)
                val maxScroll = scrollDepths[random.nextInt(scrollDepths.size)]
                for (depth in scrollDepths) {
                    if (depth > maxScroll || remaining <= 0) break
                    Events.insert {
                        it[Events.projectId] = projectId
                        it[Events.visitorHash] = visitorHash
                        it[Events.sessionId] = sessionId
                        it[Events.eventType] = "scroll"
                        it[Events.path] = path
                        it[Events.country] = country
                        it[Events.city] = city
                        it[Events.browser] = browser
                        it[Events.os] = os
                        it[Events.device] = device
                        it[Events.scrollDepth] = depth
                        it[Events.region] = regions[country]?.random()
                        it[Events.latitude] = coords?.first
                        it[Events.longitude] = coords?.second
                        it[Events.timestamp] = currentTimestamp.plusSeconds(depth.toLong())
                    }
                    inserted++
                    remaining--
                }
            }

            // Generate occasional outbound/download events
            if (!isFirstEvent && random.nextDouble() < 0.1 && remaining > 0) {
                val isDownload = random.nextBoolean()
                Events.insert {
                    it[Events.projectId] = projectId
                    it[Events.visitorHash] = visitorHash
                    it[Events.sessionId] = sessionId
                    it[Events.eventType] = if (isDownload) "download" else "outbound"
                    it[Events.eventName] = if (isDownload) "report.pdf" else "github.com"
                    it[Events.path] = path
                    it[Events.country] = country
                    it[Events.city] = city
                    it[Events.browser] = browser
                    it[Events.os] = os
                    it[Events.device] = device
                    it[Events.targetUrl] = if (isDownload) downloadUrls.random() else outboundUrls.random()
                    it[Events.region] = regions[country]?.random()
                    it[Events.latitude] = coords?.first
                    it[Events.longitude] = coords?.second
                    it[Events.timestamp] = currentTimestamp.plusSeconds(5)
                }
                inserted++
                remaining--
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
