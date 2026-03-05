package se.onemanstudio.middleware

import com.github.benmanes.caffeine.cache.Caffeine
import java.util.concurrent.TimeUnit

/**
 * Dedicated cache for widget endpoint responses.
 * Separate from QueryCache to avoid cross-invalidation and allow longer TTL.
 * Widget data tolerates 60s staleness since it's displayed on public pages.
 */
object WidgetCache {
    private val cache = Caffeine.newBuilder()
        .maximumSize(200)
        .expireAfterWrite(60, TimeUnit.SECONDS)
        .build<String, Any>()

    /**
     * Get cached result or compute and cache it
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getOrCompute(key: String, compute: () -> T): T {
        return cache.get(key) { compute() as Any } as T
    }

    /**
     * Invalidate all widget cache entries for a specific project
     */
    fun invalidateProject(projectId: String) {
        cache.asMap().keys.removeIf { it.startsWith("widget:$projectId") }
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
            "hitRate" to stats.hitRate()
        )
    }
}
