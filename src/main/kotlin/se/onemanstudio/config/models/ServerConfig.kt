package se.onemanstudio.config.models

/**
 * Server configuration
 */
data class ServerConfig(
    val port: Int,
    val isDevelopment: Boolean
)
