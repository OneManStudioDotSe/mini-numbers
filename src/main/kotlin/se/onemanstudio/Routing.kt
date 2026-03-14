package se.onemanstudio

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import se.onemanstudio.config.models.AppConfig
import se.onemanstudio.middleware.RateLimiter
import se.onemanstudio.routing.*

fun Application.configureRouting(config: AppConfig, rateLimiter: RateLimiter) {
    routing {
        // Public, non-rate-limited routes (tracker, metrics, etc.)
        publicRoutes(config)
        
        // Collection endpoint (rate-limited)
        collectionRoutes(rateLimiter)

        // Authenticated admin API
        authenticate("session-auth") {
            route("/admin") {
                adminProjectRoutes()
                adminAnalyticsRoutes()
                adminUserRoutes()
                adminFeatureRoutes()
            }
        }
        
        // Authentication routes (login/logout)
        authRoutes()

        // Serve the main landing page
        staticResources("/", "static") {
            default("index.html")
        }
        
        // Serve the Admin SPA dashboard
        staticResources("/admin-panel", "static") {
            default("admin.html")
        }
    }
}
