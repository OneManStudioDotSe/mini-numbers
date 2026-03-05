import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import se.onemanstudio.config.models.AppConfig
import se.onemanstudio.middleware.RateLimiter
import se.onemanstudio.routing.*

/**
 * Install all application routes — public endpoints, data collection, and the
 * authenticated admin panel API.
 *
 * This implementation is modularized into the `se.onemanstudio.routing` package.
 */
fun Application.configureRouting(config: AppConfig, rateLimiter: RateLimiter) {
    val privacyMode = config.privacy.privacyMode

    routing {
        // Publicly accessible routes (health, metrics, static landing pages)
        publicRoutes(config)

        // Data collection (unauthenticated but rate-limited)
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
