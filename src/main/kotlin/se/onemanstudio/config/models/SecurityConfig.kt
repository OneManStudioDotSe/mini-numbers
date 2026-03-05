package se.onemanstudio.config.models

/**
 * Security-related configuration
 */
data class SecurityConfig(
    val adminUsername: String,
    val adminPassword: String,
    val serverSalt: String,
    val allowedOrigins: List<String>
)
