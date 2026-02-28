package se.onemanstudio

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.SerializationException
import se.onemanstudio.api.models.ApiError
import se.onemanstudio.config.ConfigLoader
import se.onemanstudio.config.ConfigurationException
import se.onemanstudio.config.models.*
import se.onemanstudio.config.models.ServerConfig as AppServerConfig
import se.onemanstudio.core.ServiceManager
import se.onemanstudio.core.configureHTTP
import se.onemanstudio.core.configureSecurity
import se.onemanstudio.setup.configureSetupRouting

/**
 * Application entry point. Launches the embedded Netty server using
 * the port and host defined in `application.yaml`.
 */
fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

/**
 * Main Ktor application module — the single entry point for all server behavior.
 *
 * Operates in **unified mode**: both the setup wizard and the normal analytics
 * dashboard are served from the same running process. On first launch the
 * application detects that configuration is missing and redirects every request
 * to the setup wizard at `/setup`. Once the user completes set up the services
 * are initialized without restarting (zero-restart architecture).
 *
 * ## Startup sequence
 * 1. Register a JVM shutdown hook so resources are cleaned up on SIGTERM.
 * 2. Check whether `.env` / environment variables contain a valid config.
 *    - **Missing** → enter setup mode (wizard served, normal routes disabled).
 *    - **Present** → load config and initialize all services via [ServiceManager].
 * 3. Install Ktor plugins that are always needed (JSON content negotiation,
 *    global error handler via StatusPages).
 * 4. Install authentication (sessions + JWT). This is installed early —
 *    even before config exists — so that the security plugin is present
 *    when routes are registered. Dynamic credential validation inside
 *    `Security.kt` handles the case where config is loaded later.
 * 5. Install unified routing (setup wizard + analytics routes).
 *
 * @see ServiceManager  for the service lifecycle state machine.
 * @see configureUnifiedRouting for how routes are conditionally installed.
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

    // Global error handler — catch unhandled exceptions and return proper JSON errors.
    // Order matters: most specific exception types first.
    install(StatusPages) {
        exception<SerializationException> { call, cause ->
            call.application.environment.log.warn("Serialization error: ${cause.message}")
            call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid request format"))
        }
        exception<io.ktor.server.plugins.ContentTransformationException> { call, cause ->
            call.application.environment.log.warn("Content transformation error: ${cause.message}")
            call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid request body format"))
        }
        exception<io.ktor.server.plugins.BadRequestException> { call, cause ->
            call.application.environment.log.warn("Bad request: ${cause.message}")
            call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid request"))
        }
        exception<IllegalArgumentException> { call, cause ->
            call.application.environment.log.warn("Bad argument: ${cause.message}")
            call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest(cause.message ?: "Invalid argument"))
        }
        exception<org.jetbrains.exposed.exceptions.ExposedSQLException> { call, cause ->
            call.application.environment.log.error("Database error: ${cause.message}", cause)
            call.respond(HttpStatusCode.InternalServerError,
                ApiError.internalError("A database error occurred"))
        }
        exception<java.sql.SQLException> { call, cause ->
            call.application.environment.log.error("Database connection error: ${cause.message}", cause)
            call.respond(HttpStatusCode.InternalServerError,
                ApiError.internalError("Database unavailable"))
        }
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled exception: ${cause.message}", cause)
            call.respond(HttpStatusCode.InternalServerError,
                ApiError.internalError("An unexpected error occurred"))
        }
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respond(HttpStatusCode.NotFound,
                ApiError.notFound("Resource not found"))
        }
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
 * Install all HTTP routes depending on the current service state.
 *
 * If a valid [AppConfig] exists **and** [ServiceManager] is ready, the full
 * set of routes is installed: CORS, data-collection (`POST /collect`),
 * admin panel, widget endpoints, etc. The setup wizard routes are **always**
 * installed because they contain the redirect logic that sends users to
 * `/setup` when the application is not yet configured.
 *
 * This function is called once during startup. After a successful setup-wizard save, [ServiceManager.reload] re-initializes services and the
 * user is redirected to the admin panel — no server restart required.
 */
private fun Application.configureUnifiedRouting() {
    // Load configuration if available
    val config = if (!ConfigLoader.isSetupNeeded()) {
        try {
            ConfigLoader.load()
        } catch (_: Exception) {
            null
        }
    } else {
        null
    }

    // Configure HTTP and routing plugins if config available and services ready
    // Note: Authentication is installed early in module() for zero-restart capability
    if (config != null && ServiceManager.isReady()) {
        val rateLimiter = se.onemanstudio.middleware.RateLimiter(
            maxTokensPerIp = config.rateLimit.perIpRequestsPerMinute,
            maxTokensPerApiKey = config.rateLimit.perApiKeyRequestsPerMinute
        )
        configureHTTP(config)
        configureRouting(config, rateLimiter)
        configureWidgetRouting(config, rateLimiter)
    }

    // Always install setup routes (they handle state-based redirects)
    configureSetupRouting()
}
