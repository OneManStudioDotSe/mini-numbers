package se.onemanstudio.middleware.models

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Token bucket for rate limiting
 * Stores available tokens and last refill time
 */
data class RateLimitBucket(
    val tokens: AtomicInteger,
    val lastRefillTime: AtomicLong
)
