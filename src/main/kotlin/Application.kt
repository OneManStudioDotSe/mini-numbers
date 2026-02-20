package se.onemanstudio

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import se.onemanstudio.config.ConfigLoader
import se.onemanstudio.config.ConfigurationException
import se.onemanstudio.config.models.*
import se.onemanstudio.config.models.ServerConfig as AppServerConfig
import se.onemanstudio.core.ServiceManager
import se.onemanstudio.core.configureHTTP
import se.onemanstudio.core.configureSecurity
import se.onemanstudio.setup.configureSetupRouting

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

/**
 * Main application module
 * Unified startup mode that supports both setup and normal operation
 * Services are initialized dynamically based on configuration availability
 */
fun Application.module() {
    val logger = environment.log

    // Register shutdown hook for graceful cleanup
    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("Shutdown signal received - cleaning up resources...")
        ServiceManager.shutdown(logger)
        logger.info("Shutdown complete")
    })

    logger.info("=" .repeat(70))
    logger.info("MINI NUMBERS ANALYTICS - UNIFIED MODE")
    logger.info("=" .repeat(70))

    // Check initial setup status and load config if available
    val setupNeeded = ConfigLoader.isSetupNeeded()
    val config: AppConfig? = if (setupNeeded) {
        logger.info("Setup required: Configuration is missing or incomplete")
        logger.info("Visit http://localhost:8080/setup to complete setup")
        null
    } else {
        logger.info("Configuration found - attempting service initialization...")

        // Load configuration
        try {
            ConfigLoader.load()
        } catch (e: ConfigurationException) {
            logger.error("Configuration error: ${e.message}")
            logger.error("Please visit /setup to reconfigure")
            null
        }
    }

    // Initialize services if config is valid
    if (config != null) {
        logger.info("Configuration loaded successfully")
        logger.info("Database type: ${config.database.type}")
        logger.info("Server port: ${config.server.port}")
        logger.info("Development mode: ${config.server.isDevelopment}")

        val initialized = ServiceManager.initialize(config, logger)
        if (initialized) {
            logger.info("Services ready - normal mode active")
        } else {
            logger.error("Service initialization failed")
            logger.error("Error: ${ServiceManager.getLastError()?.message}")
        }
    }

    // Install core plugins (always needed)
    install(ContentNegotiation) {
        json()
    }

    // Install authentication early (with dynamic config loading for zero-restart)
    // This must be installed before routing, even if config doesn't exist yet
    // The dynamic config loading in Security.kt will handle credential validation
    configureSecurity(config ?: AppConfig(
        security = SecurityConfig(
            adminUsername = "",
            adminPassword = "",
            serverSalt = "",
            allowedOrigins = emptyList()
        ),
        database = DatabaseConfig(
            type = DatabaseType.SQLITE,
            path = ""
        ),
        server = AppServerConfig(8080, true),
        geoip = GeoIPConfig(""),
        rateLimit = RateLimitConfig(1000, 10000)
    ))

    // Configure routing (unified - both setup and normal routes)
    configureUnifiedRouting()

    logger.info("=" .repeat(70))
    logger.info("APPLICATION READY")
    if (!setupNeeded && ServiceManager.isReady()) {
        logger.info("Admin panel: http://localhost:8080/admin-panel")
    } else {
        logger.info("Setup wizard: http://localhost:8080/setup")
    }
    logger.info("=" .repeat(70))
}

/**
 * Configure unified routing for both setup and normal modes
 * Dynamically installs plugins and routes based on service availability
 */
private fun Application.configureUnifiedRouting() {
    // Load configuration if available
    val config = if (!ConfigLoader.isSetupNeeded()) {
        try {
            ConfigLoader.load()
        } catch (e: Exception) {
            null
        }
    } else {
        null
    }

    // Configure HTTP and routing plugins if config available and services ready
    // Note: Authentication is installed early in module() for zero-restart capability
    if (config != null && ServiceManager.isReady()) {
        configureHTTP(config)
        configureRouting(config)
    }

    // Always install setup routes (they handle state-based redirects)
    configureSetupRouting()
}
