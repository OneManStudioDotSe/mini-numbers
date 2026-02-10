package se.onemanstudio.core

import com.asyncapi.kotlinasyncapi.context.service.AsyncApiExtension
import com.asyncapi.kotlinasyncapi.ktor.AsyncApiPlugin
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import se.onemanstudio.config.AppConfig

/**
 * Configure HTTP plugins including AsyncAPI and CORS
 * CORS policy adapts based on development vs production mode
 *
 * @param config Application configuration
 */
fun Application.configureHTTP(config: AppConfig) {
    // AsyncAPI documentation plugin
    install(AsyncApiPlugin) {
        extension = AsyncApiExtension.builder {
            info {
                title("Mini Numbers Analytics API")
                version("1.0.0")
            }
        }
    }

    // CORS configuration with smart development/production detection
    install(CORS) {
        // Allow standard HTTP methods
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)

        // Allow required headers
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader("X-Project-Key")  // Custom header for API key

        // Smart CORS policy based on environment
        if (config.server.isDevelopment) {
            // Development mode: allow all origins for easier testing
            anyHost()
        } else {
            // Production mode: use whitelist from configuration
            if (config.security.allowedOrigins.isEmpty()) {
                // No origins configured = reject all cross-origin requests (most secure)
            } else if (config.security.allowedOrigins.contains("*")) {
                // Wildcard configuration (not recommended but allowed)
                anyHost()
            } else {
                // Use specific origin whitelist
                config.security.allowedOrigins.forEach { origin ->
                    allowHost(origin, schemes = listOf("http", "https"))
                }
            }
        }
    }

    // Log CORS configuration
    if (config.server.isDevelopment) {
        environment.log.info("CORS: Development mode - allowing all origins")
    } else {
        if (config.security.allowedOrigins.isEmpty()) {
            environment.log.warn("CORS: No allowed origins configured. All cross-origin requests will be rejected.")
        } else if (config.security.allowedOrigins.contains("*")) {
            environment.log.warn("CORS: Wildcard (*) configured in production. This is not recommended for security.")
        } else {
            environment.log.info("CORS: Production mode - allowed origins: ${config.security.allowedOrigins.joinToString(", ")}")
        }
    }
}
