package se.onemanstudio.middleware

import org.junit.Test
import se.onemanstudio.middleware.models.RateLimitResult
import kotlin.test.*

/**
 * Unit tests for RateLimiter
 * Tests token bucket algorithm, IP/API key limits, and rate limit status
 */
class RateLimiterTest {

    @Test
    fun `first request is always allowed`() {
        val limiter = RateLimiter(maxTokensPerIp = 10, maxTokensPerApiKey = 100)

        val result = limiter.checkRateLimit("192.168.1.1", "key-1")

        assertTrue(result is RateLimitResult.Allowed)
    }

    @Test
    fun `requests within IP limit are allowed`() {
        val limiter = RateLimiter(maxTokensPerIp = 5, maxTokensPerApiKey = 100)

        // All 5 requests should be allowed
        repeat(5) { i ->
            val result = limiter.checkRateLimit("192.168.1.1", "key-1")
            assertTrue(result is RateLimitResult.Allowed, "Request $i should be allowed")
        }
    }

    @Test
    fun `exceeding IP limit returns Exceeded result`() {
        val limiter = RateLimiter(maxTokensPerIp = 3, maxTokensPerApiKey = 100)

        // Use all 3 tokens
        repeat(3) { limiter.checkRateLimit("192.168.1.1", "key-1") }

        // 4th request should be rate limited
        val result = limiter.checkRateLimit("192.168.1.1", "key-1")

        assertTrue(result is RateLimitResult.Exceeded)
        assertEquals("IP", (result as RateLimitResult.Exceeded).limitType)
        assertEquals("192.168.1.1", result.identifier)
        assertEquals(3, result.limit)
    }

    @Test
    fun `exceeding API key limit returns Exceeded result`() {
        val limiter = RateLimiter(maxTokensPerIp = 100, maxTokensPerApiKey = 3)

        // Use all 3 API key tokens (from different IPs to avoid IP limit)
        repeat(3) { i ->
            limiter.checkRateLimit("192.168.1.$i", "shared-key")
        }

        // 4th request should be rate limited by API key
        val result = limiter.checkRateLimit("192.168.1.99", "shared-key")

        assertTrue(result is RateLimitResult.Exceeded)
        assertEquals("API_KEY", (result as RateLimitResult.Exceeded).limitType)
        assertEquals("shared-key", result.identifier)
    }

    @Test
    fun `different IPs have separate rate limit buckets`() {
        val limiter = RateLimiter(maxTokensPerIp = 2, maxTokensPerApiKey = 100)

        // Exhaust IP 1's limit
        repeat(2) { limiter.checkRateLimit("192.168.1.1", "key-1") }
        val result1 = limiter.checkRateLimit("192.168.1.1", "key-1")
        assertTrue(result1 is RateLimitResult.Exceeded)

        // IP 2 should still be allowed
        val result2 = limiter.checkRateLimit("192.168.1.2", "key-1")
        assertTrue(result2 is RateLimitResult.Allowed)
    }

    @Test
    fun `different API keys have separate rate limit buckets`() {
        val limiter = RateLimiter(maxTokensPerIp = 100, maxTokensPerApiKey = 2)

        // Exhaust key-1's limit (from different IPs)
        repeat(2) { i -> limiter.checkRateLimit("10.0.0.$i", "key-1") }
        val result1 = limiter.checkRateLimit("10.0.0.99", "key-1")
        assertTrue(result1 is RateLimitResult.Exceeded)

        // key-2 should still be allowed
        val result2 = limiter.checkRateLimit("10.0.0.99", "key-2")
        assertTrue(result2 is RateLimitResult.Allowed)
    }

    @Test
    fun `IP limit is checked before API key limit`() {
        val limiter = RateLimiter(maxTokensPerIp = 1, maxTokensPerApiKey = 1)

        // First request uses both tokens
        limiter.checkRateLimit("192.168.1.1", "key-1")

        // Second request should hit IP limit first
        val result = limiter.checkRateLimit("192.168.1.1", "key-1")

        assertTrue(result is RateLimitResult.Exceeded)
        assertEquals("IP", (result as RateLimitResult.Exceeded).limitType)
    }

    @Test
    fun `getRateLimitStatus returns remaining tokens`() {
        val limiter = RateLimiter(maxTokensPerIp = 10, maxTokensPerApiKey = 100)

        // Make 3 requests
        repeat(3) { limiter.checkRateLimit("192.168.1.1", "key-1") }

        val status = limiter.getRateLimitStatus("192.168.1.1", "key-1")

        assertEquals(7, status.ipTokensRemaining) // 10 - 3
        assertEquals(10, status.ipTokensMax)
        assertEquals(97, status.apiKeyTokensRemaining) // 100 - 3
        assertEquals(100, status.apiKeyTokensMax)
    }

    @Test
    fun `getRateLimitStatus for unknown IP returns max tokens`() {
        val limiter = RateLimiter(maxTokensPerIp = 50, maxTokensPerApiKey = 200)

        val status = limiter.getRateLimitStatus("unknown-ip", "unknown-key")

        assertEquals(50, status.ipTokensRemaining)
        assertEquals(50, status.ipTokensMax)
        assertEquals(200, status.apiKeyTokensRemaining)
        assertEquals(200, status.apiKeyTokensMax)
    }

    @Test
    fun `rate limiter with limit of 1 allows exactly one request`() {
        val limiter = RateLimiter(maxTokensPerIp = 1, maxTokensPerApiKey = 100)

        val first = limiter.checkRateLimit("192.168.1.1", "key-1")
        val second = limiter.checkRateLimit("192.168.1.1", "key-1")

        assertTrue(first is RateLimitResult.Allowed)
        assertTrue(second is RateLimitResult.Exceeded)
    }

    @Test
    fun `Exceeded result includes window information`() {
        val limiter = RateLimiter(maxTokensPerIp = 1, maxTokensPerApiKey = 100)

        limiter.checkRateLimit("192.168.1.1", "key-1")
        val result = limiter.checkRateLimit("192.168.1.1", "key-1")

        assertTrue(result is RateLimitResult.Exceeded)
        assertEquals("1 minute", (result as RateLimitResult.Exceeded).window)
    }
}
