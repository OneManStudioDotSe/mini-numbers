package se.onemanstudio.setup

import se.onemanstudio.core.Lab
import se.onemanstudio.core.Labs
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import se.onemanstudio.config.ConfigLoader
import se.onemanstudio.config.ConfigurationException
import se.onemanstudio.configureRouting
import se.onemanstudio.setup.models.*
import se.onemanstudio.configureWidgetRouting
import se.onemanstudio.middleware.RateLimiter
import se.onemanstudio.core.AppInfo
import se.onemanstudio.core.ServiceManager
import se.onemanstudio.core.configureHTTP
import se.onemanstudio.db.DatabaseFactory
import se.onemanstudio.middleware.RedirectValidator
import java.io.File
import java.security.SecureRandom

/**
 * Configure routing for setup wizard mode
 * Only serves setup-related endpoints when configuration is missing
 */
fun Application.configureSetupRouting() {
    routing {
        // Root route - MUST be defined first to avoid conflicts
        // Intelligent redirect based on application state
        get("/") {
            when {
                ConfigLoader.isSetupNeeded() -> call.respondRedirect(RedirectValidator.safeRedirect("/setup", "/setup"))
                ServiceManager.isReady() -> call.respondRedirect(RedirectValidator.safeRedirect("/admin-panel"))
                else -> {
                    call.respondText(
                        """
                        Configuration loaded but services failed to initialize.

                        The reason is in the server log. Fix the configuration (.env or the environment) and restart.
                        """.trimIndent(),
                        status = HttpStatusCode.ServiceUnavailable
                    )
                }
            }
        }

        // Serve static setup resources (wizard HTML, CSS, JS)
        staticResources("/setup", "setup") {
            default("wizard.html")
        }

        // Shared CSS, JS and images. Registered here because these routes are installed in every
        // state - the wizard needs them before setup, and every other page after it.
        staticResources("/assets", "assets")
        staticResources("/css", "static/css")
        staticResources("/js", "static/js")

        // API: Check setup status with servicesReady flag
        get("/setup/api/status") {
            try {
                val setupNeeded = ConfigLoader.isSetupNeeded()
                val servicesReady = ServiceManager.isReady()

                call.respond(
                    SetupStatusResponse(
                        setupNeeded = setupNeeded,
                        servicesReady = servicesReady,
                        message = when {
                            setupNeeded -> "Configuration is missing or incomplete"
                            !servicesReady -> "Configuration exists but services not ready"
                            else -> "Configuration complete and services ready"
                        }
                    )
                )
            } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
                environment.log.error("Error checking setup status", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(error = "Failed to check setup status")
                )
            }
        }

        // API: Generate secure salt
        get("/setup/api/generate-salt") {
            try {
                val salt = generateSecureToken(64)
                call.respond(SaltResponse(salt = salt))
            } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
                environment.log.error("Error generating salt", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(error = "Failed to generate salt")
                )
            }
        }

        // Health check endpoint for Docker/platform deployments
        // Always accessible, never behind auth
        get("/health") {
            val state = ServiceManager.getState()
            val databaseOk = if (state == ServiceManager.State.READY) DatabaseFactory.probe() else null
            val (code, status) = healthStatus(state, databaseOk)

            call.respond(
                code,
                buildJsonObject {
                    put("status", status)
                    put("state", state.toString())
                    put("version", AppInfo.VERSION)
                    put("uptimeSeconds", ServiceManager.getUptimeSeconds())
                    put("servicesReady", ServiceManager.isReady())
                    put("setupNeeded", ConfigLoader.isSetupNeeded())
                    databaseOk?.let { put("database", if (it) "ok" else "unreachable") }
                    if (state == ServiceManager.State.ERROR) {
                        put("message", "Service initialization failed; see the server log")
                    }
                }
            )
        }

        // API: Save configuration and initialize services (NO RESTART!)
        post("/setup/api/save") {
            // Guard: the wizard only creates the first configuration, and only for whoever holds the setup token
            val refusal = setupSaveRefusal(
                configExists = ServiceManager.isReady() || !ConfigLoader.isSetupNeeded(),
                tokenMatches = SetupToken.matches(call.request.headers["X-Setup-Token"])
            )
            if (refusal != null) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse(error = refusal))
                return@post
            }

            try {
                // Parse configuration from request
                val config = try {
                    call.receive<SetupConfigDTO>()
                } catch (e: kotlinx.serialization.SerializationException) {
                    call.respond(HttpStatusCode.BadRequest,
                        ErrorResponse(error = "Invalid JSON format: ${e.message}"))
                    return@post
                } catch (_: io.ktor.server.plugins.BadRequestException) {
                    call.respond(HttpStatusCode.BadRequest,
                        ErrorResponse(error = "Invalid request format"))
                    return@post
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest,
                        ErrorResponse(error = "Invalid configuration data: ${e.message}"))
                    return@post
                }

                // Validate configuration server-side
                val validation = SetupValidation.validateSetupConfig(config)

                if (!validation.valid) {
                    call.respond(HttpStatusCode.BadRequest, validation)
                    return@post
                }

                // Build .env file content
                val envContent = buildEnvContent(config)

                // Write .env file atomically with backup
                try {
                    writeEnvFileAtomic(envContent)
                    environment.log.info("Configuration saved successfully to .env")
                } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
                    environment.log.error("Failed to write .env file", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(error = "Failed to write configuration file: ${e.message}")
                    )
                    return@post
                }

                // Reload configuration and initialize services
                environment.log.info("Configuration saved - reloading and initializing services...")

                val reloadedConfig = try {
                    ConfigLoader.reload()
                } catch (e: ConfigurationException) {
                    environment.log.error("Failed to reload configuration: ${e.message}")
                    revertEnvFile()
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(error = "This configuration could not be loaded, nothing was saved: ${e.message}")
                    )
                    return@post
                }

                // Initialize services with new configuration
                val initialized = ServiceManager.reload(reloadedConfig, environment.log)

                if (initialized) {
                    environment.log.info("Services initialized successfully - application ready!")

                    // Install HTTP/Routing plugins dynamically
                    // Note: Authentication is installed early in Application.module() with dynamic config loading
                    // for seamless zero-restart credential updates
                    try {
                        val rateLimiter = RateLimiter(
                            maxTokensPerIp = reloadedConfig.rateLimit.perIpRequestsPerMinute,
                            maxTokensPerApiKey = reloadedConfig.rateLimit.perApiKeyRequestsPerMinute
                        )
                        configureHTTP(reloadedConfig)
                        configureRouting(reloadedConfig, rateLimiter)
                        if (Labs.isOn(Lab.WIDGETS)) configureWidgetRouting(rateLimiter)
                    } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
                        environment.log.warn("Could not install plugins dynamically: ${e.message}")
                        // This is expected if plugins are already installed - not a fatal error
                    }

                    call.respond(
                        SaveResponse(
                            success = true,
                            message = "Configuration saved and services initialized successfully!"
                        )
                    )
                } else {
                    val error = ServiceManager.getLastError()
                    environment.log.error("Service initialization failed: ${error?.message}")
                    // Take the configuration back, so the wizard stays open for another try
                    revertEnvFile()
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(
                            error = "Services could not start with this configuration, nothing was saved: ${error?.message}"
                        )
                    )
                }

                // NO exitProcess() call - services are ready NOW!

            } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
                environment.log.error("Error saving configuration", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(error = "Failed to save configuration: ${e.message}")
                )
            }
        }
    }
}

/**
 * Generate cryptographically secure random token
 * @param length Number of characters (hex string will be twice this length in bytes)
 */
private fun generateSecureToken(length: Int): String {
    val random = SecureRandom()
    val bytes = ByteArray(length)
    random.nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
}

/**
 * Build .env file content from setup configuration
 */
private fun buildEnvContent(config: SetupConfigDTO): String {
    val lines = mutableListOf<String>()

    lines.add("# Mini Numbers Analytics Configuration")
    lines.add("# Generated by Setup Wizard on ${java.time.LocalDateTime.now()}")
    lines.add("")

    // Security configuration
    lines.add("# ============================================")
    lines.add("# Security (REQUIRED)")
    lines.add("# ============================================")
    lines.add("ADMIN_USERNAME=${config.adminUsername}")

    // Hash password with BCrypt before storing (rounds=12 for good security/performance balance)
    val hashedPassword = se.onemanstudio.core.Passwords.hash(config.adminPassword)
    lines.add("# Password is stored as BCrypt hash for security")
    lines.add("ADMIN_PASSWORD=${hashedPassword}")

    lines.add("SERVER_SALT=${config.serverSalt}")
    lines.add("PRIVACY_MODE=${config.privacyMode}")
    lines.add("")

    // CORS configuration
    lines.add("# ============================================")
    lines.add("# CORS Configuration")
    lines.add("# ============================================")
    lines.add("# Comma-separated list of allowed origins (empty = no CORS, * = all origins)")
    lines.add("ALLOWED_ORIGINS=${config.allowedOrigins}")
    lines.add("")

    // Database configuration
    lines.add("# ============================================")
    lines.add("# Database Configuration")
    lines.add("# ============================================")
    lines.add("DB_TYPE=${config.database.type.uppercase()}")

    if (config.database.type.uppercase() == "SQLITE") {
        lines.add("DB_SQLITE_PATH=${config.database.sqlitePath}")
    } else if (config.database.type.uppercase() == "POSTGRESQL") {
        lines.add("DB_PG_HOST=${config.database.pgHost}")
        lines.add("DB_PG_PORT=${config.database.pgPort}")
        lines.add("DB_PG_NAME=${config.database.pgName}")
        lines.add("DB_PG_USERNAME=${config.database.pgUsername}")
        lines.add("DB_PG_PASSWORD=${config.database.pgPassword}")
        if (config.database.pgMaxPoolSize != null) {
            lines.add("DB_PG_MAX_POOL_SIZE=${config.database.pgMaxPoolSize}")
        }
    }
    lines.add("")

    // Server configuration
    lines.add("# ============================================")
    lines.add("# Server Configuration")
    lines.add("# ============================================")
    lines.add("SERVER_PORT=${config.server.port}")
    lines.add("KTOR_DEVELOPMENT=${config.server.isDevelopment}")
    lines.add("")

    // GeoIP configuration
    lines.add("# ============================================")
    lines.add("# GeoIP Configuration")
    lines.add("# ============================================")
    lines.add("GEOIP_DATABASE_PATH=${config.geoip.databasePath}")
    lines.add("")

    // Rate limiting configuration
    lines.add("# ============================================")
    lines.add("# Rate Limiting")
    lines.add("# ============================================")
    lines.add("RATE_LIMIT_PER_IP=${config.rateLimit.perIp}")
    lines.add("RATE_LIMIT_PER_API_KEY=${config.rateLimit.perApiKey}")
    lines.add("")

    return lines.joinToString("\n")
}

/**
 * Write .env file atomically with backup
 * Safety measures:
 * 1. Write to temporary file first
 * 2. Backup existing .env (if exists)
 * 3. Atomic rename from temp to .env
 * 4. Set file permissions (owner only)
 */
private fun writeEnvFileAtomic(content: String) {
    val tempFile = File(".env.tmp")
    val envFile = File(".env")
    val backupFile = File(".env.backup")

    // Step 1: Write to temporary file
    tempFile.writeText(content)

    // Step 2: Backup existing .env if it exists
    if (envFile.exists()) {
        envFile.copyTo(backupFile, overwrite = true)
    } else {
        backupFile.delete() // a leftover backup must not be restored by revertEnvFile
    }

    // Step 3: Atomic rename (move temp to .env)
    if (!tempFile.renameTo(envFile)) {
        // If rename fails, try copy + delete
        tempFile.copyTo(envFile, overwrite = true)
        tempFile.delete()
    }

    // Step 4: Set file permissions (readable and writable by owner only)
    try {
        envFile.setReadable(true, true)
        envFile.setWritable(true, true)
        envFile.setExecutable(false)
    } catch (_: SecurityException) {
        // Permission setting might fail on some systems, but file is still created
    }
}

/**
 * Undo [writeEnvFileAtomic] after a save whose configuration did not start: put the previous
 * `.env` back (or remove the new one), so setup is still "needed" and the wizard can be retried.
 */
private fun revertEnvFile() {
    val envFile = File(".env")
    val backupFile = File(".env.backup")
    ConfigLoader.forget()
    if (backupFile.exists()) {
        backupFile.copyTo(envFile, overwrite = true)
    } else {
        envFile.delete()
    }
}

/**
 * What `/health` answers: 503 when services failed to start or the database cannot be reached,
 * 200 "ok" while the setup wizard is still needed, 200 "healthy" once everything runs.
 */
internal fun healthStatus(state: ServiceManager.State, databaseOk: Boolean?): Pair<HttpStatusCode, String> = when {
    state == ServiceManager.State.ERROR -> HttpStatusCode.ServiceUnavailable to "unhealthy"
    databaseOk == false -> HttpStatusCode.ServiceUnavailable to "unhealthy"
    state == ServiceManager.State.READY -> HttpStatusCode.OK to "healthy"
    else -> HttpStatusCode.OK to "ok"
}
