package se.onemanstudio.core

import com.asyncapi.kotlinasyncapi.context.service.AsyncApiExtension
import com.asyncapi.kotlinasyncapi.ktor.AsyncApiPlugin
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import se.onemanstudio.config.models.AppConfig

/**
 * Configure HTTP plugins including AsyncAPI and CORS
 * CORS policy adapts based on development vs production mode
 *
 * @param config Application configuration
 */
fun Application.configureHTTP(config: AppConfig) {
    // Security headers for defense-in-depth
    install(DefaultHeaders) {
        // Prevent MIME type sniffing (forces browser to respect Content-Type)
        header("X-Content-Type-Options", "nosniff")

        // Prevent clickjacking attacks (deny embedding in iframes)
        header("X-Frame-Options", "DENY")

        // Enable XSS protection in older browsers (modern browsers use CSP)
        header("X-XSS-Protection", "1; mode=block")

        // Content Security Policy - restrict resource loading
        // Allows same-origin scripts/styles, inline styles (for admin panel), and CDN resources
        header("Content-Security-Policy",
            "default-src 'self'; " +
            "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://unpkg.com; " +  // Allow CDN scripts (Chart.js, Feather, Leaflet)
            "style-src 'self' 'unsafe-inline' https://unpkg.com https://cdn.jsdelivr.net; " +   // Allow CDN styles (Leaflet, Remix Icon)
            "font-src 'self' https://cdn.jsdelivr.net; " +  // Allow CDN fonts (Remix Icon)
            "img-src 'self' data: https:; " +        // Allow images from same origin, data URIs, and HTTPS
            "connect-src 'self' https://cdn.jsdelivr.net https://unpkg.com; " +  // Allow AJAX to same origin and CDN source maps
            "frame-ancestors 'none'; " +             // Prevent embedding (redundant with X-Frame-Options)
            "base-uri 'self'; " +                    // Prevent base tag injection
            "form-action 'self'"                     // Prevent form submission to external sites
        )

        // Referrer Policy - control what referrer information is sent
        header("Referrer-Policy", "strict-origin-when-cross-origin")

        // Only enable HSTS in production mode (not in development/localhost)
        if (!config.server.isDevelopment) {
            // HTTP Strict Transport Security - force HTTPS for 1 year
            header("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
        }
    }

    // AsyncAPI documentation plugin
    install(AsyncApiPlugin) {
        extension = AsyncApiExtension.builder {
            info {
                title("Mini Numbers Analytics API")
                version("1.0.0")
            }
        }
    }

    // CORS configuration
    // anyHost() is required for embeddable widget endpoints that run on third-party sites.
    // This is safe because allowCredentials is false (default), so session cookies are never
    // sent cross-origin. Auth is enforced at the application level: session auth for admin
    // endpoints, API keys for /collect and /widget endpoints.
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)

        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader("X-Project-Key")
        allowHeader("X-Widget-Key")

        anyHost()
    }

    environment.log.info("CORS: All origins allowed (credentials disabled, auth enforced by session/API keys)")
}
