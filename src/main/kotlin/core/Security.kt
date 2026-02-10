package se.onemanstudio.core

import io.ktor.server.application.*
import io.ktor.server.auth.*
import se.onemanstudio.config.AppConfig

/**
 * Configure authentication for the admin panel
 * Uses Basic HTTP Authentication with credentials from environment configuration
 *
 * @param config Application configuration containing security settings
 */
fun Application.configureSecurity(config: AppConfig) {
    // Capture environment for logging inside callbacks
    val logger = environment.log

    install(Authentication) {
        basic("admin-auth") {
            realm = "Access to the admin panel"
            validate { credentials ->
                // Validate credentials from configuration
                if (credentials.name == config.security.adminUsername &&
                    credentials.password == config.security.adminPassword) {
                    UserIdPrincipal(credentials.name)
                } else {
                    // Log failed authentication attempts for security monitoring
                    logger.warn("Failed authentication attempt for user: ${credentials.name}")
                    null
                }
            }
        }
    }
}
