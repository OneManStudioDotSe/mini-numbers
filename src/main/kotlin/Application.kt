package se.onemanstudio

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import se.onemanstudio.config.ConfigLoader
import se.onemanstudio.config.ConfigurationException
import se.onemanstudio.core.configureHTTP
import se.onemanstudio.core.configureSecurity
import se.onemanstudio.db.DatabaseFactory
import se.onemanstudio.services.GeoLocationService
import se.onemanstudio.utils.AnalyticsSecurity

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

/**
 * Main application module
 * Initializes configuration, services, and plugins in the correct order
 */
fun Application.module() {
    // 1. Load configuration FIRST (fail fast if invalid)
    val config = try {
        ConfigLoader.load()
    } catch (e: ConfigurationException) {
        environment.log.error("Configuration error: ${e.message}")
        throw e  // Fail fast - application cannot start without valid config
    }

    environment.log.info("Configuration loaded successfully")
    environment.log.info("Database type: ${config.database.type}")
    environment.log.info("Server port: ${config.server.port}")
    environment.log.info("Development mode: ${config.server.isDevelopment}")

    // 2. Initialize security module with server salt
    try {
        AnalyticsSecurity.init(config.security.serverSalt)
        environment.log.info("Security module initialized")
    } catch (e: Exception) {
        environment.log.error("Failed to initialize security: ${e.message}")
        throw e
    }

    // 3. Initialize database connection
    try {
        DatabaseFactory.init(config.database)
        environment.log.info("Database initialized successfully")
    } catch (e: Exception) {
        environment.log.error("Failed to initialize database: ${e.message}")
        throw e
    }

    // 4. Initialize GeoIP service
    try {
        GeoLocationService.init(config.geoip.databasePath)
        environment.log.info("GeoIP service initialized")
    } catch (e: Exception) {
        environment.log.warn("GeoIP service initialization failed: ${e.message}")
        environment.log.warn("Location tracking will be disabled")
        // Don't throw - application can work without GeoIP
    }

    // 5. Install plugins
    install(ContentNegotiation) {
        json()
    }

    // 6. Configure application features with config
    configureHTTP(config)
    configureSecurity(config)
    configureRouting(config)

    environment.log.info("Mini Numbers Analytics started successfully!")
}
