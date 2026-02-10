package se.onemanstudio.utils

import java.security.MessageDigest
import java.time.LocalDate

/**
 * Security utilities for analytics
 * Provides privacy-preserving visitor identification using daily-rotating hashes
 */
object AnalyticsSecurity {

    /**
     * Server salt for visitor hash generation
     * Must be initialized before use via init() function
     * Loaded from SERVER_SALT environment variable
     */
    private lateinit var serverSalt: String

    /**
     * Initialize the security module with server salt
     * Must be called once during application startup
     *
     * @param salt Cryptographically secure random string (min 32 characters)
     * @throws IllegalArgumentException if salt is too short
     */
    fun init(salt: String) {
        if (salt.length < 32) {
            throw IllegalArgumentException("Server salt must be at least 32 characters long for security")
        }
        serverSalt = salt
    }

    /**
     * Generate privacy-preserving visitor hash
     * Hash rotates daily to prevent cross-day tracking
     *
     * @param ip Client IP address (not stored, only used for hashing)
     * @param userAgent Client user agent string (not stored, only used for hashing)
     * @param projectId Project UUID
     * @return SHA-256 hash as hex string
     * @throws UninitializedPropertyAccessException if init() was not called
     */
    fun generateVisitorHash(ip: String, userAgent: String, projectId: String): String {
        // Daily salt causes hash to change each day
        // Prevents tracking visitors across multiple days
        val dateSalt = LocalDate.now().toString()

        // Combine all inputs with server salt
        val input = ip + userAgent + projectId + serverSalt + dateSalt

        // Generate SHA-256 hash
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
