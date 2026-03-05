package se.onemanstudio.setup.models

import kotlinx.serialization.Serializable

/**
 * Server configuration for setup wizard
 */
@Serializable
data class ServerSetupDTO(
    val port: Int,
    val isDevelopment: Boolean
)
