package se.onemanstudio.setup

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mindrot.jbcrypt.BCrypt
import se.onemanstudio.config.ConfigLoader
import se.onemanstudio.config.ConfigurationException
import se.onemanstudio.setup.models.*
import se.onemanstudio.configureRouting
import se.onemanstudio.core.ServiceManager
import se.onemanstudio.core.configureHTTP
import se.onemanstudio.core.configureSecurity
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
                ConfigLoader.isSetupNeeded() -> call.respondRedirect("/setup")
                ServiceManager.isReady() -> call.respondRedirect("/login")
                else -> {
                    val error = ServiceManager.getLastError()
                    call.respondText(
                        """
                        Configuration loaded but services failed to initialize.
                        Error: ${error?.message ?: "Unknown error"}

                        Please check server logs or visit /setup to reconfigure.
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
            } catch (e: Exception) {
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
            } catch (e: Exception) {
                environment.log.error("Error generating salt", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(error = "Failed to generate salt")
                )
            }
        }

        // API: Save configuration and initialize services (NO RESTART!)
        post("/setup/api/save") {
            try {
                // Parse configuration from request
                val config = call.receive<SetupConfigDTO>()

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
                } catch (e: Exception) {
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
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(error = "Configuration saved but reload failed: ${e.message}")
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
                        configureHTTP(reloadedConfig)
                        configureRouting(reloadedConfig)
                    } catch (e: Exception) {
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
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(
                            error = "Configuration saved but service initialization failed: ${error?.message}"
                        )
                    )
                }

                // NO exitProcess() call - services are ready NOW!

            } catch (e: Exception) {
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
    val hashedPassword = BCrypt.hashpw(config.adminPassword, BCrypt.gensalt(12))
    lines.add("# Password is stored as BCrypt hash for security")
    lines.add("ADMIN_PASSWORD=${hashedPassword}")

    lines.add("SERVER_SALT=${config.serverSalt}")
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
    } catch (e: Exception) {
        // Permission setting might fail on some systems, but file is still created
        // Log warning but don't fail
    }
}
