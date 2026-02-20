package se.onemanstudio.config.models

/**
 * Root configuration for Mini Numbers application
 * All settings loaded from environment variables or .env file
 */
data class AppConfig(
    val security: SecurityConfig,
    val database: DatabaseConfig,
    val server: ServerConfig,
    val geoip: GeoIPConfig,
    val rateLimit: RateLimitConfig,
    val privacy: PrivacyConfig = PrivacyConfig(),
    val tracker: TrackerConfig = TrackerConfig()
)
