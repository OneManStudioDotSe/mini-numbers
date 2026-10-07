package se.onemanstudio

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import se.onemanstudio.config.models.AppConfig
import se.onemanstudio.middleware.AdminCorsGuard
import se.onemanstudio.middleware.RateLimiter
import se.onemanstudio.middleware.enforceRateLimit
import se.onemanstudio.routing.*

/** Credential endpoints get a much tighter bucket than the rest of the API: BCrypt verification is CPU-bound. */
private const val AUTH_LIMIT_PER_IP_PER_MINUTE = 20
private const val AUTH_LIMIT_GLOBAL_PER_MINUTE = 300

fun Application.configureRouting(config: AppConfig, rateLimiter: RateLimiter) {
    val privacyMode = config.privacy.privacyMode
    val allowedOrigins = config.security.allowedOrigins
    val authLimiter = RateLimiter(
        maxTokensPerIp = AUTH_LIMIT_PER_IP_PER_MINUTE,
        maxTokensPerApiKey = AUTH_LIMIT_GLOBAL_PER_MINUTE
    )

    routing {
        // Public, non-rate-limited routes (tracker, metrics, etc.)
        publicRoutes(config)
        
        // Collection endpoint (rate-limited)
        collectionRoutes(rateLimiter, privacyMode)

        // Authentication & Password Reset (rate-limited per IP)
        authRoutes(authLimiter)

        // Protected Admin API (supports both Session and JWT)
        authenticate("admin-session", "api-jwt") {
            route("/admin") {
                guardAdminRequests(allowedOrigins, rateLimiter)
                adminProjectRoutes()
                adminAnalyticsRoutes()
                adminFeatureRoutes()
                adminUserRoutes()
            }

            // Also expose as /api for JWT programmatic access
            route("/api") {
                guardAdminRequests(allowedOrigins, rateLimiter)
                adminProjectRoutes()
                adminAnalyticsRoutes()
                adminFeatureRoutes()
                adminUserRoutes()
            }

            // Serve the Admin SPA dashboard
            staticResources("/admin-panel", "static") {
                default("admin.html")
            }
        }
    }
}

/** Origin allowlist (`ALLOWED_ORIGINS`) and per-IP rate limiting for every admin API call. */
private fun Route.guardAdminRequests(allowedOrigins: List<String>, rateLimiter: RateLimiter) {
    intercept(ApplicationCallPipeline.Plugins) {
        if (!AdminCorsGuard.check(call, allowedOrigins) || !call.enforceRateLimit(rateLimiter, "admin")) {
            finish()
        }
    }
}
