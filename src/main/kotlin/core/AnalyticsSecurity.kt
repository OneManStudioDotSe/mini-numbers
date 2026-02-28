package se.onemanstudio.core

import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Privacy-preserving visitor identification using rotating SHA-256 hashes.
 *
 * Instead of cookies or persistent identifiers, Mini Numbers hashes the
 * combination of IP + User-Agent + project ID + server salt + a time-based
 * rotation bucket. The resulting 64-character hex string is stored in the
 * `visitor_hash` column and used to count unique visitors.
 *
 * ## Privacy guarantees
 * - The raw IP address only exists in RAM for the duration of the request.
 * - The hash cannot be reversed to recover the original IP or User-Agent.
 * - The rotation bucket changes every [hashRotationHours] hours (default 24),
 *   so the same real visitor produces a **different** hash after each rotation.
 *   This prevents long-term tracking while still allowing short-term uniqueness.
 *
 * ## Thread safety
 * [serverSalt] and [hashRotationHours] are `@Volatile` and mutated only
 * inside `@Synchronized` [init], making the object safe for concurrent reads
 * from Ktor's coroutine-based request handlers.
 *
 * @see ServiceManager.initialize where this module is initialised on startup.
 */
object AnalyticsSecurity {

    /** The secret salt mixed into every hash. Must be at least 32 characters. */
    @Volatile
    private var serverSalt: String? = null

    /** How often (in hours) the rotation bucket rolls over, invalidating old hashes. */
    @Volatile
    private var hashRotationHours: Int = 24

    fun isInitialized(): Boolean = serverSalt != null

    /**
     * Initialise the security module.
     *
     * @param salt       Server-wide secret (min 32 chars). Changing this
     *                   invalidates **all** existing visitor hashes.
     * @param rotationHours How often hashes rotate (1 – 8 760 h, i.e. up to 1 year).
     * @throws IllegalArgumentException if [salt] is too short.
     */
    @Synchronized
    fun init(salt: String, rotationHours: Int = 24) {
        if (salt.length < 32) {
            throw IllegalArgumentException("Server salt must be at least 32 characters long for security")
        }
        serverSalt = salt
        hashRotationHours = rotationHours.coerceIn(1, 8760)
    }

    /**
     * Generate a privacy-preserving visitor hash.
     *
     * The hash is deterministic for the **same** inputs within the same
     * rotation bucket, so repeated page-views from the same browser in a
     * short window are correctly de-duplicated. Once the bucket rolls over,
     * the same visitor produces a new hash and is counted as "new".
     *
     * Formula: `SHA-256(ip + userAgent + projectId + serverSalt + rotationBucket)`
     *
     * @param ip        Raw client IP (never stored, only used in-memory).
     * @param userAgent Raw User-Agent header.
     * @param projectId The project's UUID string — isolates hashes per project.
     * @return 64-character lowercase hex string.
     * @throws IllegalStateException if [init] has not been called yet.
     */
    fun generateVisitorHash(ip: String, userAgent: String, projectId: String): String {
        val salt = serverSalt ?: throw IllegalStateException(
            "Security module not initialized. Call init() first."
        )

        // Calculate rotation bucket based on configured hours
        val now = LocalDateTime.now()
        val epochHours = ChronoUnit.HOURS.between(LocalDateTime.of(2024, 1, 1, 0, 0), now)
        val rotationBucket = epochHours / hashRotationHours

        val input = ip + userAgent + projectId + salt + rotationBucket

        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    /**
     * Get the current hash rotation period in hours
     */
    fun getRotationHours(): Int = hashRotationHours
}
