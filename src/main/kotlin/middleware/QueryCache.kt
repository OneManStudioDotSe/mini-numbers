package se.onemanstudio.middleware

import com.github.benmanes.caffeine.cache.Caffeine
import java.util.concurrent.TimeUnit

/**
 * Query result cache using Caffeine for dashboard and report queries.
 * Short TTL ensures data freshness while reducing database load.
 */
object QueryCache {
    private val cache = Caffeine.newBuilder()
        .maximumSize(500)
        .expireAfterWrite(30, TimeUnit.SECONDS)
        .build<String, Any>()

    /**
     * Get cached result or compute and cache it
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getOrCompute(key: String, compute: () -> T): T {
        return cache.get(key) { compute() } as T
    }

    /**
     * Invalidate all cache entries for a specific project
     */
    fun invalidateProject(projectId: String) {
        cache.asMap().keys.removeIf { it.startsWith(projectId) }
    }

    /**
     * Invalidate all cache entries
     */
    fun invalidateAll() {
        cache.invalidateAll()
    }

    /**
     * Get cache statistics for monitoring
     */
    fun stats(): Map<String, Any> {
        val stats = cache.stats()
        return mapOf(
            "size" to cache.estimatedSize(),
            "hitRate" to stats.hitRate(),
            "hitCount" to stats.hitCount(),
            "missCount" to stats.missCount(),
            "evictionCount" to stats.evictionCount()
        )
    }
}
