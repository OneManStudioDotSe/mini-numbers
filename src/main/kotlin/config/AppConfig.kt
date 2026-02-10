package se.onemanstudio.config

/**
 * Root configuration for Mini Numbers application
 * All settings loaded from environment variables or .env file
 */
data class AppConfig(
    val security: SecurityConfig,
    val database: DatabaseConfig,
    val server: ServerConfig,
    val geoip: GeoIPConfig,
    val rateLimit: RateLimitConfig
)

/**
 * Security-related configuration
 */
data class SecurityConfig(
    val adminUsername: String,
    val adminPassword: String,
    val serverSalt: String,
    val allowedOrigins: List<String>
)

/**
 * Database configuration supporting both SQLite and PostgreSQL
 */
data class DatabaseConfig(
    val type: DatabaseType,
    val path: String? = null,           // For SQLite
    val host: String? = null,           // For PostgreSQL
    val port: Int? = null,              // For PostgreSQL
    val name: String? = null,           // For PostgreSQL
    val username: String? = null,       // For PostgreSQL
    val password: String? = null,       // For PostgreSQL
    val maxPoolSize: Int = 3            // For PostgreSQL connection pooling
)

/**
 * Database type enumeration
 */
enum class DatabaseType {
    SQLITE,
    POSTGRESQL
}

/**
 * Server configuration
 */
data class ServerConfig(
    val port: Int,
    val isDevelopment: Boolean
)

/**
 * GeoIP database configuration
 */
data class GeoIPConfig(
    val databasePath: String
)

/**
 * Rate limiting configuration
 */
data class RateLimitConfig(
    val perIpRequestsPerMinute: Int,
    val perApiKeyRequestsPerMinute: Int
)

/**
 * Exception thrown when configuration is invalid or missing required values
 */
class ConfigurationException(message: String) : Exception(message)
