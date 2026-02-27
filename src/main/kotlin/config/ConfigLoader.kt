package se.onemanstudio.config

import se.onemanstudio.config.models.*
import java.io.File

/**
 * Configuration loader that reads from environment variables and .env file
 *
 * Loading priority:
 * 1. Environment variables (highest priority)
 * 2. .env file in project root
 * 3. Fail with clear error if required values missing
 */
object ConfigLoader {

    private const val ENV_FILE_PATH = ".env"

    /**
     * Check if setup wizard is needed
     * Returns true if required configuration is missing from all sources
     * (environment variables, system properties, and .env file)
     */
    fun isSetupNeeded(): Boolean {
        // Try to load .env file if it exists (sets system properties)
        try {
            loadDotEnvFile()
        } catch (_: Exception) {
            // .env loading failed - fall through to check other sources
        }

        // Check for required configuration from any source (env vars, system properties, or .env)
        val hasAdminPassword = getEnvOrNull("ADMIN_PASSWORD")?.isNotBlank() == true
        val hasServerSalt = getEnvOrNull("SERVER_SALT")?.isNotBlank() == true

        // Setup is needed if either required field is missing
        return !(hasAdminPassword && hasServerSalt)
    }

    /**
     * Reload configuration from file system
     * Used when configuration is updated (e.g., after setup wizard completes)
     *
     * This method clears any previously loaded system properties from the .env file
     * and reloads the configuration fresh from the file system.
     *
     * @return Freshly loaded application configuration
     * @throws ConfigurationException if configuration is invalid
     */
    fun reload(): AppConfig {
        // Clear previously loaded system properties from .env
        // (Only clears system properties, not actual environment variables)
        val envFile = File(ENV_FILE_PATH)
        if (envFile.exists()) {
            envFile.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachLine

                val separatorIndex = trimmed.indexOf("=")
                if (separatorIndex > 0) {
                    val key = trimmed.substring(0, separatorIndex).trim()
                    // Only clear if it's a system property (not an env var)
                    if (System.getenv(key) == null) {
                        System.clearProperty(key)
                    }
                }
            }
        }

        // Reload fresh configuration
        return load()
    }

    /**
     * Load complete application configuration
     * Fails fast with clear error messages if required config is missing
     */
    fun load(): AppConfig {
        // Step 1: Load .env file if exists
        loadDotEnvFile()

        // Step 2: Build configuration from environment
        return AppConfig(
            security = loadSecurityConfig(),
            database = loadDatabaseConfig(),
            server = loadServerConfig(),
            geoip = loadGeoIPConfig(),
            rateLimit = loadRateLimitConfig(),
            privacy = loadPrivacyConfig(),
            tracker = loadTrackerConfig(),
            email = loadEmailConfig()
        )
    }

    /**
     * Load .env file and set system properties
     * Environment variables take precedence over .env file
     */
    private fun loadDotEnvFile() {
        val envFile = File(ENV_FILE_PATH)
        if (!envFile.exists()) {
            return
        }

        envFile.forEachLine { line ->
            val trimmed = line.trim()

            // Skip empty lines and comments
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                return@forEachLine
            }

            // Parse KEY=VALUE format
            val separatorIndex = trimmed.indexOf("=")
            if (separatorIndex > 0) {
                val key = trimmed.substring(0, separatorIndex).trim()
                val value = trimmed.substring(separatorIndex + 1).trim()

                // Only set if not already provided via env vars or system properties
                // (system properties from Gradle/test config take precedence over .env)
                if (System.getenv(key) == null && System.getProperty(key) == null) {
                    System.setProperty(key, value)
                }
            }
        }
    }

    /**
     * Get environment variable or null if not set
     */
    private fun getEnvOrNull(key: String): String? {
        return System.getenv(key) ?: System.getProperty(key)
    }

    /**
     * Get environment variable or default value if not set
     */
    private fun getEnvOrDefault(key: String, default: String): String {
        return getEnvOrNull(key) ?: default
    }

    /**
     * Get required environment variable or throw ConfigurationException
     */
    private fun getEnvRequired(key: String): String {
        return getEnvOrNull(key)
            ?: throw ConfigurationException(
                "Required configuration missing: $key\n" +
                "Please set this environment variable or add it to .env file.\n" +
                "See .env.example for configuration template."
            )
    }

    /**
     * Load security configuration
     */
    private fun loadSecurityConfig(): SecurityConfig {
        val adminUsername = getEnvOrDefault("ADMIN_USERNAME", "admin")
        val adminPassword = getEnvRequired("ADMIN_PASSWORD")
        val serverSalt = getEnvRequired("SERVER_SALT")

        // Validate server salt strength
        if (serverSalt.length < 32) {
            throw ConfigurationException(
                "SERVER_SALT must be at least 32 characters long for security.\n" +
                "Current length: ${serverSalt.length}\n" +
                "Generate a strong salt with: openssl rand -hex 64"
            )
        }

        // Parse allowed origins (comma-separated list)
        val originsString = getEnvOrDefault("ALLOWED_ORIGINS", "")
        val allowedOrigins = if (originsString.isBlank()) {
            emptyList()
        } else {
            originsString.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }

        return SecurityConfig(
            adminUsername = adminUsername,
            adminPassword = adminPassword,
            serverSalt = serverSalt,
            allowedOrigins = allowedOrigins
        )
    }

    /**
     * Load database configuration
     */
    private fun loadDatabaseConfig(): DatabaseConfig {
        val typeString = getEnvOrDefault("DB_TYPE", "SQLITE")
        val type = try {
            DatabaseType.valueOf(typeString.uppercase())
        } catch (e: IllegalArgumentException) {
            throw ConfigurationException(
                "Invalid DB_TYPE: $typeString\n" +
                "Must be SQLITE or POSTGRESQL"
            )
        }

        return when (type) {
            DatabaseType.SQLITE -> DatabaseConfig(
                type = DatabaseType.SQLITE,
                path = getEnvOrDefault("DB_SQLITE_PATH", "./stats.db"),
                host = null,
                port = null,
                name = null,
                username = null,
                password = null
            )
            DatabaseType.POSTGRESQL -> {
                val maxPoolSizeStr = getEnvOrDefault("DB_PG_MAX_POOL_SIZE", "3")
                val maxPoolSize = try {
                    maxPoolSizeStr.toInt()
                } catch (e: NumberFormatException) {
                    throw ConfigurationException(
                        "Invalid DB_PG_MAX_POOL_SIZE: $maxPoolSizeStr\n" +
                        "Must be a positive integer"
                    )
                }

                DatabaseConfig(
                    type = DatabaseType.POSTGRESQL,
                    path = null,
                    host = getEnvOrDefault("DB_PG_HOST", "localhost"),
                    port = getEnvOrDefault("DB_PG_PORT", "5432").toIntOrNull()
                        ?: throw ConfigurationException("Invalid DB_PG_PORT: must be a number"),
                    name = getEnvOrDefault("DB_PG_NAME", "mini_numbers"),
                    username = getEnvOrDefault("DB_PG_USERNAME", "postgres"),
                    password = getEnvRequired("DB_PG_PASSWORD"),
                    maxPoolSize = maxPoolSize
                )
            }
        }
    }

    /**
     * Load server configuration
     */
    private fun loadServerConfig(): ServerConfig {
        val portStr = getEnvOrDefault("SERVER_PORT", "8080")
        val port = try {
            portStr.toInt()
        } catch (e: NumberFormatException) {
            throw ConfigurationException(
                "Invalid SERVER_PORT: $portStr\n" +
                "Must be a valid port number (1-65535)"
            )
        }

        if (port < 1 || port > 65535) {
            throw ConfigurationException(
                "Invalid SERVER_PORT: $port\n" +
                "Must be between 1 and 65535"
            )
        }

        val isDevelopment = getEnvOrDefault("KTOR_DEVELOPMENT", "false")
            .lowercase() == "true"

        return ServerConfig(
            port = port,
            isDevelopment = isDevelopment
        )
    }

    /**
     * Load GeoIP configuration
     */
    private fun loadGeoIPConfig(): GeoIPConfig {
        val databasePath = getEnvOrDefault(
            "GEOIP_DATABASE_PATH",
            "src/main/resources/geo/geolite2-city.mmdb"
        )

        return GeoIPConfig(
            databasePath = databasePath
        )
    }

    /**
     * Load rate limiting configuration
     */
    private fun loadRateLimitConfig(): RateLimitConfig {
        val perIpStr = getEnvOrDefault("RATE_LIMIT_PER_IP", "1000")
        val perApiKeyStr = getEnvOrDefault("RATE_LIMIT_PER_API_KEY", "10000")

        val perIp = try {
            perIpStr.toInt()
        } catch (e: NumberFormatException) {
            throw ConfigurationException(
                "Invalid RATE_LIMIT_PER_IP: $perIpStr\n" +
                "Must be a positive integer"
            )
        }

        val perApiKey = try {
            perApiKeyStr.toInt()
        } catch (e: NumberFormatException) {
            throw ConfigurationException(
                "Invalid RATE_LIMIT_PER_API_KEY: $perApiKeyStr\n" +
                "Must be a positive integer"
            )
        }

        if (perIp < 1) {
            throw ConfigurationException("RATE_LIMIT_PER_IP must be at least 1")
        }

        if (perApiKey < 1) {
            throw ConfigurationException("RATE_LIMIT_PER_API_KEY must be at least 1")
        }

        return RateLimitConfig(
            perIpRequestsPerMinute = perIp,
            perApiKeyRequestsPerMinute = perApiKey
        )
    }

    /**
     * Load privacy configuration
     */
    private fun loadPrivacyConfig(): PrivacyConfig {
        val hashRotationHours = getEnvOrDefault("HASH_ROTATION_HOURS", "24").toIntOrNull() ?: 24
        val privacyModeStr = getEnvOrDefault("PRIVACY_MODE", "STANDARD").uppercase()
        val privacyMode = try {
            PrivacyMode.valueOf(privacyModeStr)
        } catch (e: IllegalArgumentException) {
            PrivacyMode.STANDARD
        }
        val dataRetentionDays = getEnvOrDefault("DATA_RETENTION_DAYS", "0").toIntOrNull() ?: 0

        return PrivacyConfig(
            hashRotationHours = hashRotationHours.coerceIn(1, 8760),
            privacyMode = privacyMode,
            dataRetentionDays = dataRetentionDays.coerceAtLeast(0)
        )
    }

    /**
     * Load tracker configuration
     */
    private fun loadTrackerConfig(): TrackerConfig {
        val heartbeatInterval = getEnvOrDefault("TRACKER_HEARTBEAT_INTERVAL", "30").toIntOrNull() ?: 30
        val spaTracking = getEnvOrDefault("TRACKER_SPA_ENABLED", "true").lowercase() == "true"

        return TrackerConfig(
            heartbeatIntervalSeconds = heartbeatInterval.coerceIn(5, 300),
            spaTrackingEnabled = spaTracking
        )
    }

    /**
     * Load email/SMTP configuration (optional)
     */
    private fun loadEmailConfig(): EmailConfig {
        return EmailConfig(
            smtpHost = getEnvOrNull("SMTP_HOST"),
            smtpPort = getEnvOrDefault("SMTP_PORT", "587").toIntOrNull() ?: 587,
            smtpUsername = getEnvOrNull("SMTP_USERNAME"),
            smtpPassword = getEnvOrNull("SMTP_PASSWORD"),
            smtpFrom = getEnvOrNull("SMTP_FROM"),
            smtpStartTls = getEnvOrDefault("SMTP_STARTTLS", "true").lowercase() == "true"
        )
    }
}
