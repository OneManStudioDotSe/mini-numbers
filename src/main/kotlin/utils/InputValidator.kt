package se.onemanstudio.utils

import se.onemanstudio.models.PageViewPayload

/**
 * Input validation and sanitization for incoming data
 * Prevents injection attacks and ensures data integrity
 */
object InputValidator {

    // Maximum field lengths (match database schema)
    private const val MAX_PATH_LENGTH = 512
    private const val MAX_REFERRER_LENGTH = 512
    private const val MAX_SESSION_ID_LENGTH = 64
    private const val MAX_EVENT_TYPE_LENGTH = 20

    // Valid event types
    private val VALID_EVENT_TYPES = setOf("pageview", "heartbeat")

    // Regex patterns for validation
    // Path: alphanumeric + common URL characters
    private val PATH_REGEX = Regex("^[a-zA-Z0-9/_\\-?=&.#%+]*$")

    // Referrer: valid HTTP/HTTPS URL
    private val URL_REGEX = Regex("^https?://[a-zA-Z0-9.-]+(:[0-9]+)?(/.*)?$")

    // Session ID: alphanumeric + hyphens
    private val SESSION_ID_REGEX = Regex("^[a-zA-Z0-9-]+$")

    /**
     * Validation result with error details
     */
    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String> = emptyList()
    ) {
        companion object {
            fun success() = ValidationResult(true, emptyList())
            fun failure(errors: List<String>) = ValidationResult(false, errors)
        }
    }

    /**
     * Validate PageViewPayload
     * Returns validation result with all errors (if any)
     */
    fun validatePageViewPayload(payload: PageViewPayload): ValidationResult {
        val errors = mutableListOf<String>()

        // Validate path
        if (payload.path.isEmpty()) {
            errors.add("Path cannot be empty")
        } else {
            if (payload.path.length > MAX_PATH_LENGTH) {
                errors.add("Path exceeds maximum length of $MAX_PATH_LENGTH characters")
            }
            if (!PATH_REGEX.matches(payload.path)) {
                errors.add("Path contains invalid characters. Allowed: alphanumeric, /, _, -, ?, =, &, ., #, %, +")
            }
        }

        // Validate referrer (optional field)
        payload.referrer?.let { referrer ->
            if (referrer.isNotEmpty()) {
                if (referrer.length > MAX_REFERRER_LENGTH) {
                    errors.add("Referrer exceeds maximum length of $MAX_REFERRER_LENGTH characters")
                }
                if (!URL_REGEX.matches(referrer)) {
                    errors.add("Referrer must be a valid HTTP or HTTPS URL")
                }
            }
        }

        // Validate session ID
        if (payload.sessionId.isEmpty()) {
            errors.add("Session ID cannot be empty")
        } else {
            if (payload.sessionId.length > MAX_SESSION_ID_LENGTH) {
                errors.add("Session ID exceeds maximum length of $MAX_SESSION_ID_LENGTH characters")
            }
            if (!SESSION_ID_REGEX.matches(payload.sessionId)) {
                errors.add("Session ID contains invalid characters. Allowed: alphanumeric and hyphens")
            }
        }

        // Validate event type
        if (payload.type.isEmpty()) {
            errors.add("Event type cannot be empty")
        } else {
            if (payload.type.length > MAX_EVENT_TYPE_LENGTH) {
                errors.add("Event type exceeds maximum length of $MAX_EVENT_TYPE_LENGTH characters")
            }
            if (payload.type !in VALID_EVENT_TYPES) {
                errors.add("Event type must be one of: ${VALID_EVENT_TYPES.joinToString(", ")}")
            }
        }

        return if (errors.isEmpty()) {
            ValidationResult.success()
        } else {
            ValidationResult.failure(errors)
        }
    }

    /**
     * Sanitize string input to prevent injection attacks
     * - Removes control characters (0x00-0x1F, 0x7F)
     * - Normalizes whitespace
     * - Trims leading/trailing whitespace
     */
    fun sanitize(input: String): String {
        return input
            .replace(Regex("[\\x00-\\x1F\\x7F]"), "") // Remove control characters
            .replace(Regex("\\s+"), " ")               // Normalize whitespace
            .trim()                                     // Trim edges
    }

    /**
     * Validate and sanitize path
     * Returns sanitized path or throws exception if invalid
     */
    fun validateAndSanitizePath(path: String): String {
        val sanitized = sanitize(path)
        if (sanitized.isEmpty()) {
            throw IllegalArgumentException("Path cannot be empty")
        }
        if (sanitized.length > MAX_PATH_LENGTH) {
            throw IllegalArgumentException("Path exceeds maximum length of $MAX_PATH_LENGTH characters")
        }
        if (!PATH_REGEX.matches(sanitized)) {
            throw IllegalArgumentException("Path contains invalid characters")
        }
        return sanitized
    }

    /**
     * Validate and sanitize session ID
     * Returns sanitized session ID or throws exception if invalid
     */
    fun validateAndSanitizeSessionId(sessionId: String): String {
        val sanitized = sanitize(sessionId)
        if (sanitized.isEmpty()) {
            throw IllegalArgumentException("Session ID cannot be empty")
        }
        if (sanitized.length > MAX_SESSION_ID_LENGTH) {
            throw IllegalArgumentException("Session ID exceeds maximum length of $MAX_SESSION_ID_LENGTH characters")
        }
        if (!SESSION_ID_REGEX.matches(sanitized)) {
            throw IllegalArgumentException("Session ID contains invalid characters")
        }
        return sanitized
    }
}
