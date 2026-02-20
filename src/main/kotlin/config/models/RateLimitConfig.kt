package se.onemanstudio.config.models

/**
 * Rate limiting configuration
 */
data class RateLimitConfig(
    val perIpRequestsPerMinute: Int,
    val perApiKeyRequestsPerMinute: Int
)
