package se.onemanstudio.core

import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Security utilities for analytics
 * Provides privacy-preserving visitor identification using configurable rotating hashes
 */
object AnalyticsSecurity {

    @Volatile
    private var serverSalt: String? = null

    @Volatile
    private var hashRotationHours: Int = 24

    fun isInitialized(): Boolean = serverSalt != null

    /**
     * Initialize the security module with server salt and optional rotation period
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
     * Generate privacy-preserving visitor hash
     * Hash rotates based on configured rotation period to prevent long-term tracking
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
