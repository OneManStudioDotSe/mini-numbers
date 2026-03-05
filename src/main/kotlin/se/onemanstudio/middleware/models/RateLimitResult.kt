package se.onemanstudio.middleware.models

/**
 * Result of rate limit check
 */
sealed class RateLimitResult {
    /**
     * Request is allowed
     */
    object Allowed : RateLimitResult()

    /**
     * Rate limit exceeded
     *
     * @param limitType Type of limit exceeded (IP or API_KEY)
     * @param identifier The IP or API key that exceeded the limit
     * @param limit Maximum requests allowed
     * @param window Time window for the limit
     */
    data class Exceeded(
        val limitType: String,
        val identifier: String,
        val limit: Int,
        val window: String
    ) : RateLimitResult()
}
