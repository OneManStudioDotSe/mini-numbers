package se.onemanstudio

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import se.onemanstudio.config.models.AppConfig
import se.onemanstudio.middleware.RateLimiter
import se.onemanstudio.routing.*

fun Application.configureRouting(config: AppConfig, rateLimiter: RateLimiter) {
    val privacyMode = config.privacy.privacyMode

    routing {
        // Public, non-rate-limited routes (tracker, metrics, etc.)
        publicRoutes(config)
        
        // Collection endpoint (rate-limited)
        collectionRoutes(rateLimiter, privacyMode)

        // Authentication & Password Reset
        authRoutes()

        // Protected Admin API (supports both Session and JWT)
        authenticate("admin-session", "api-jwt") {
            route("/admin") {
                adminProjectRoutes()
                adminAnalyticsRoutes()
                adminFeatureRoutes()
                adminUserRoutes()
            }

            // Also expose as /api for JWT programmatic access
            route("/api") {
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
