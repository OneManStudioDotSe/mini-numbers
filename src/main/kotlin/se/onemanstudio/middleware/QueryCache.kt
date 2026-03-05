package se.onemanstudio.middleware

import com.github.benmanes.caffeine.cache.Caffeine
import java.util.concurrent.TimeUnit

/**
 * In-memory query result cache (Caffeine, 500 entries, 30 s TTL).
 *
 * Sits between the admin API endpoints and the heavy SQL queries in
 * `DataAnalysisUtils` / `ConversionAnalysisUtils` / `RevenueAnalysisUtils`.
 * Keys follow the pattern `"{projectId}:{endpoint}:{filter}"` so that
 * [invalidateProject] can wipe all cached results for a single project
 * when new events arrive (called from the `POST /collect` handler).
 *
 * The 30-second TTL is a deliberate trade-off: short enough that the
 * dashboard feels "live", long enough to absorb rapid page refreshes
 * and multiple API calls triggered by a single dashboard load.
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
    fun <T : Any> getOrCompute(key: String, compute: () -> T): T {
        return cache.get(key) { compute() as Any } as T
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
