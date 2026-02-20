package se.onemanstudio.middleware

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import se.onemanstudio.middleware.models.RateLimitBucket
import se.onemanstudio.middleware.models.RateLimitResult
import se.onemanstudio.middleware.models.RateLimitStatus
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Rate limiter using token bucket algorithm
 * Provides separate limits for IP addresses and API keys
 */
class RateLimiter(
    private val maxTokensPerIp: Int,
    private val maxTokensPerApiKey: Int
) {
    // Cache for IP-based rate limiting
    // Expires after 5 minutes of inactivity to prevent memory bloat
    private val ipBuckets: Cache<String, RateLimitBucket> = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofMinutes(5))
        .maximumSize(10_000)
        .build()

    // Cache for API key-based rate limiting
    private val apiKeyBuckets: Cache<String, RateLimitBucket> = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofMinutes(5))
        .maximumSize(1_000)
        .build()

    /**
     * Check if request is allowed based on IP and API key rate limits
     * Both limits must pass for the request to be allowed
     *
     * @param ip Client IP address
     * @param apiKey Project API key
     * @return RateLimitResult indicating if allowed or which limit was exceeded
     */
    fun checkRateLimit(ip: String, apiKey: String): RateLimitResult {
        val ipAllowed = checkBucket(ip, maxTokensPerIp, ipBuckets)
        val apiKeyAllowed = checkBucket(apiKey, maxTokensPerApiKey, apiKeyBuckets)

        return when {
            !ipAllowed -> RateLimitResult.Exceeded(
                limitType = "IP",
                identifier = ip,
                limit = maxTokensPerIp,
                window = "1 minute"
            )
            !apiKeyAllowed -> RateLimitResult.Exceeded(
                limitType = "API_KEY",
                identifier = apiKey,
                limit = maxTokensPerApiKey,
                window = "1 minute"
            )
            else -> RateLimitResult.Allowed
        }
    }

    /**
     * Check if bucket has available tokens
     * Implements token bucket algorithm with per-minute refill
     *
     * @param key Identifier (IP or API key)
     * @param maxTokens Maximum tokens per minute
     * @param cache Cache storing buckets
     * @return true if request is allowed, false if rate limit exceeded
     */
    private fun checkBucket(key: String, maxTokens: Int, cache: Cache<String, RateLimitBucket>): Boolean {
        // Get or create bucket for this key
        val bucket = cache.get(key) {
            RateLimitBucket(
                tokens = AtomicInteger(maxTokens),
                lastRefillTime = AtomicLong(System.currentTimeMillis())
            )
        }!!

        // Synchronize on bucket to prevent race conditions
        // This ensures check-and-update is atomic across concurrent requests
        synchronized(bucket) {
            // Calculate elapsed time since last refill
            val now = System.currentTimeMillis()
            val lastRefill = bucket.lastRefillTime.get()
            val elapsedMinutes = (now - lastRefill) / 60_000.0

            // Refill tokens if a minute has passed
            if (elapsedMinutes >= 1.0) {
                // Full refill after 1 minute
                bucket.tokens.set(maxTokens)
                bucket.lastRefillTime.set(now)
            }

            // Try to consume a token (atomic check-and-decrement)
            val currentTokens = bucket.tokens.get()
            if (currentTokens > 0) {
                bucket.tokens.decrementAndGet()
                return true
            }

            // No tokens available - rate limit exceeded
            return false
        }
    }

    /**
     * Get current rate limit status for debugging
     * Returns remaining tokens for IP and API key
     */
    fun getRateLimitStatus(ip: String, apiKey: String): RateLimitStatus {
        val ipBucket = ipBuckets.getIfPresent(ip)
        val apiKeyBucket = apiKeyBuckets.getIfPresent(apiKey)

        return RateLimitStatus(
            ipTokensRemaining = ipBucket?.tokens?.get() ?: maxTokensPerIp,
            ipTokensMax = maxTokensPerIp,
            apiKeyTokensRemaining = apiKeyBucket?.tokens?.get() ?: maxTokensPerApiKey,
            apiKeyTokensMax = maxTokensPerApiKey
        )
    }
}
