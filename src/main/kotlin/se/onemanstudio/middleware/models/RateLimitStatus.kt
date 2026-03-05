package se.onemanstudio.middleware.models

/**
 * Current rate limit status for debugging
 */
data class RateLimitStatus(
    val ipTokensRemaining: Int,
    val ipTokensMax: Int,
    val apiKeyTokensRemaining: Int,
    val apiKeyTokensMax: Int
)
