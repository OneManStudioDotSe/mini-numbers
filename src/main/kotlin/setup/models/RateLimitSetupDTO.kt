package se.onemanstudio.setup.models

import kotlinx.serialization.Serializable

/**
 * Rate limiting configuration for setup wizard
 */
@Serializable
data class RateLimitSetupDTO(
    val perIp: Int,
    val perApiKey: Int
)
