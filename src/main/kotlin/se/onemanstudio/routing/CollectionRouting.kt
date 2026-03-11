package se.onemanstudio.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerializationException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import se.onemanstudio.api.models.ApiError
import se.onemanstudio.api.models.collection.PageViewPayload
import se.onemanstudio.config.models.PrivacyMode
import se.onemanstudio.core.AnalyticsSecurity
import se.onemanstudio.db.Events
import se.onemanstudio.db.Projects
import se.onemanstudio.middleware.InputValidator
import se.onemanstudio.middleware.QueryCache
import se.onemanstudio.middleware.RateLimiter
import se.onemanstudio.middleware.WidgetCache
import se.onemanstudio.middleware.models.RateLimitResult
import se.onemanstudio.services.GeoLocationService
import se.onemanstudio.services.UserAgentParser
import se.onemanstudio.services.WebhookTrigger

fun Route.collectionRoutes(rateLimiter: RateLimiter, privacyMode: PrivacyMode) {
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
        } catch (_: BadRequestException) {
            return@post call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid request payload format"))
        }

        val validationResult = InputValidator.validatePageViewPayload(payload)
        if (!validationResult.isValid) {
            call.application.environment.log.warn("Validation failed for API key ${apiKey.take(8)}****: ${validationResult.errors}")
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
                WebhookTrigger.checkAndFire(
                    projectId = project[Projects.id],
                    eventType = payload.type,
                    eventName = sanitizedEventName,
                    path = sanitizedPath
                )
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                call.application.environment.log.warn("Webhook trigger error: ${e.message}")
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            call.application.environment.log.error("Failed to save event: ${e.message}", e)
            return@post call.respond(HttpStatusCode.InternalServerError,
                ApiError.internalError("Failed to save event"))
        }

        call.respond(HttpStatusCode.Accepted)
    }
}
