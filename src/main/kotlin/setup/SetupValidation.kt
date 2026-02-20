package se.onemanstudio.setup

import se.onemanstudio.setup.models.*

/**
 * Server-side validation for setup wizard configuration
 * All validation must happen server-side - never trust client input
 */
object SetupValidation {

    /**
     * Validate complete setup configuration
     * Returns ValidationResult with all errors found (not just first error)
     */
    fun validateSetupConfig(config: SetupConfigDTO): ValidationResult {
        val errors = mutableMapOf<String, String>()

        // Validate admin credentials
        validateAdminUsername(config.adminUsername)?.let { errors["adminUsername"] = it }
        validateAdminPassword(config.adminPassword)?.let { errors["adminPassword"] = it }
        validateServerSalt(config.serverSalt)?.let { errors["serverSalt"] = it }

        // Validate allowed origins
        validateAllowedOrigins(config.allowedOrigins)?.let { errors["allowedOrigins"] = it }

        // Validate database configuration
        validateDatabase(config.database, errors)

        // Validate server configuration
        validateServer(config.server, errors)

        // Validate GeoIP configuration
        validateGeoIP(config.geoip)?.let { errors["geoipPath"] = it }

        // Validate rate limiting configuration
        validateRateLimit(config.rateLimit, errors)

        return ValidationResult(
            valid = errors.isEmpty(),
            errors = errors
        )
    }

    /**
     * Validate admin username
     */
    private fun validateAdminUsername(username: String): String? {
        return when {
            username.isBlank() -> "Admin username is required"
            username.length < 3 -> "Admin username must be at least 3 characters"
            username.length > 50 -> "Admin username must not exceed 50 characters"
            !username.matches(Regex("^[a-zA-Z0-9_-]+$")) ->
                "Admin username can only contain letters, numbers, hyphens, and underscores"
            else -> null
        }
    }

    /**
     * Validate admin password
     */
    private fun validateAdminPassword(password: String): String? {
        return when {
            password.isBlank() -> "Admin password is required"
            password.length < 8 -> "Admin password must be at least 8 characters"
            password.length > 100 -> "Admin password must not exceed 100 characters"
            else -> null
        }
    }

    /**
     * Validate server salt
     */
    private fun validateServerSalt(salt: String): String? {
        return when {
            salt.isBlank() -> "Server salt is required"
            salt.length < 32 -> "Server salt must be at least 32 characters for security (current: ${salt.length})"
            salt.length > 128 -> "Server salt must not exceed 128 characters"
            else -> null
        }
    }

    /**
     * Validate allowed origins
     */
    private fun validateAllowedOrigins(origins: String): String? {
        // Empty is allowed (means no CORS origins configured)
        if (origins.isBlank()) {
            return null
        }

        // Check if it's a wildcard
        if (origins.trim() == "*") {
            return null  // Wildcard is allowed but discouraged
        }

        // Validate each origin if comma-separated
        val originList = origins.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        for (origin in originList) {
            // Basic URL validation (must start with http:// or https://)
            if (!origin.matches(Regex("^https?://[a-zA-Z0-9.-]+(:[0-9]+)?$"))) {
                return "Invalid origin format: $origin (must be like https://example.com)"
            }
        }

        return null
    }

    /**
     * Validate database configuration
     */
    private fun validateDatabase(db: DatabaseSetupDTO, errors: MutableMap<String, String>) {
        when (db.type.uppercase()) {
            "SQLITE" -> {
                if (db.sqlitePath.isNullOrBlank()) {
                    errors["dbSqlitePath"] = "SQLite database path is required"
                } else if (db.sqlitePath.length > 255) {
                    errors["dbSqlitePath"] = "SQLite path is too long (max 255 characters)"
                }
            }
            "POSTGRESQL" -> {
                if (db.pgHost.isNullOrBlank()) {
                    errors["dbPgHost"] = "PostgreSQL host is required"
                }
                if (db.pgPort == null || db.pgPort < 1 || db.pgPort > 65535) {
                    errors["dbPgPort"] = "PostgreSQL port must be between 1 and 65535"
                }
                if (db.pgName.isNullOrBlank()) {
                    errors["dbPgName"] = "PostgreSQL database name is required"
                }
                if (db.pgUsername.isNullOrBlank()) {
                    errors["dbPgUsername"] = "PostgreSQL username is required"
                }
                if (db.pgPassword.isNullOrBlank()) {
                    errors["dbPgPassword"] = "PostgreSQL password is required"
                }
                if (db.pgMaxPoolSize != null && (db.pgMaxPoolSize < 1 || db.pgMaxPoolSize > 50)) {
                    errors["dbPgMaxPoolSize"] = "PostgreSQL pool size must be between 1 and 50"
                }
            }
            else -> {
                errors["dbType"] = "Database type must be SQLITE or POSTGRESQL"
            }
        }
    }

    /**
     * Validate server configuration
     */
    private fun validateServer(server: ServerSetupDTO, errors: MutableMap<String, String>) {
        if (server.port < 1 || server.port > 65535) {
            errors["serverPort"] = "Server port must be between 1 and 65535"
        }
        // isDevelopment is a boolean, no validation needed
    }

    /**
     * Validate GeoIP configuration
     */
    private fun validateGeoIP(geoip: GeoIPSetupDTO): String? {
        return when {
            geoip.databasePath.isBlank() -> "GeoIP database path is required"
            geoip.databasePath.length > 512 -> "GeoIP path is too long (max 512 characters)"
            else -> null
        }
    }

    /**
     * Validate rate limiting configuration
     */
    private fun validateRateLimit(rateLimit: RateLimitSetupDTO, errors: MutableMap<String, String>) {
        if (rateLimit.perIp < 1 || rateLimit.perIp > 1000000) {
            errors["rateLimitPerIp"] = "Rate limit per IP must be between 1 and 1,000,000"
        }
        if (rateLimit.perApiKey < 1 || rateLimit.perApiKey > 1000000) {
            errors["rateLimitPerApiKey"] = "Rate limit per API key must be between 1 and 1,000,000"
        }
    }
}
