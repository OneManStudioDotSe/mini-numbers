package se.onemanstudio.config.models

/**
 * Security-related configuration
 */
data class SecurityConfig(
    val adminUsername: String,
    val adminPassword: String,
    val serverSalt: String,
    val allowedOrigins: List<String>,
    /** Bearer token for `/metrics` (`METRICS_TOKEN`); without one, only a signed-in admin can read it. */
    val metricsToken: String? = null
)
