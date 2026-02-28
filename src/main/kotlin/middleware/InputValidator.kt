package se.onemanstudio.middleware

import se.onemanstudio.api.models.collection.PageViewPayload

/**
 * Input validation and sanitisation for all data arriving via `POST /collect`.
 *
 * Every field in a [PageViewPayload] is checked against:
 * - **Length limits** that match the database column sizes (e.g. path ≤ 512 chars).
 * - **Regex allow-lists** that reject characters outside the expected alphabet
 *   (prevents SQL injection, XSS, and log-forging).
 * - **Semantic rules** (e.g. `scrollDepth` must be 0-100, `eventName` is
 *   required for custom events but forbidden for pageviews).
 *
 * The [sanitize] function strips control characters and normalises whitespace;
 * it is used both inside validation and as a standalone helper for other inputs.
 *
 * Validation is **all-or-nothing**: [validatePageViewPayload] collects every
 * error into a list and returns them all at once, so the client can fix
 * multiple issues in a single round-trip.
 */
object InputValidator {

    // Maximum field lengths (match database schema)
    private const val MAX_PATH_LENGTH = 512
    private const val MAX_REFERRER_LENGTH = 512
    private const val MAX_SESSION_ID_LENGTH = 64
    private const val MAX_EVENT_TYPE_LENGTH = 20
    private const val MAX_EVENT_NAME_LENGTH = 100
    private const val MAX_UTM_LENGTH = 200
    private const val MAX_TARGET_URL_LENGTH = 1024
    private const val MAX_PROPERTIES_LENGTH = 2048

    // Valid event types
    private val VALID_EVENT_TYPES = setOf("pageview", "heartbeat", "custom", "scroll", "outbound", "download")

    // Event types that support eventName
    private val EVENT_TYPES_WITH_NAME = setOf("custom", "outbound", "download")

    // Regex patterns for validation
    // Path: alphanumeric + common URL characters
    private val PATH_REGEX = Regex("^[a-zA-Z0-9/_\\-?=&.#%+]*$")

    // Referrer: valid HTTP/HTTPS URL
    private val URL_REGEX = Regex("^https?://[a-zA-Z0-9.-]+(:[0-9]+)?(/.*)?$")

    // Session ID: alphanumeric + hyphens
    private val SESSION_ID_REGEX = Regex("^[a-zA-Z0-9-]+$")

    // Event name: alphanumeric + underscores, hyphens, dots, spaces
    private val EVENT_NAME_REGEX = Regex("^[a-zA-Z0-9_\\-. ]+$")

    // UTM fields: alphanumeric + common campaign characters
    private val UTM_REGEX = Regex("^[a-zA-Z0-9_\\-. +%()]+$")

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

        // Validate eventName (required for custom/outbound/download events)
        if (payload.type in EVENT_TYPES_WITH_NAME) {
            if (payload.eventName.isNullOrBlank()) {
                errors.add("Event name is required for ${payload.type} events")
            } else {
                if (payload.eventName.length > MAX_EVENT_NAME_LENGTH) {
                    errors.add("Event name exceeds maximum length of $MAX_EVENT_NAME_LENGTH characters")
                }
                if (!EVENT_NAME_REGEX.matches(payload.eventName)) {
                    errors.add("Event name contains invalid characters. Allowed: alphanumeric, _, -, ., space")
                }
            }
        } else if (!payload.eventName.isNullOrBlank()) {
            errors.add("Event name should only be provided for custom, outbound, or download event types")
        }

        // Validate UTM fields (all optional)
        validateUtmField(payload.utmSource, "UTM source", errors)
        validateUtmField(payload.utmMedium, "UTM medium", errors)
        validateUtmField(payload.utmCampaign, "UTM campaign", errors)
        validateUtmField(payload.utmTerm, "UTM term", errors)
        validateUtmField(payload.utmContent, "UTM content", errors)

        // Validate scrollDepth (required for scroll events, must be 0-100)
        if (payload.type == "scroll") {
            if (payload.scrollDepth == null) {
                errors.add("Scroll depth is required for scroll events")
            } else if (payload.scrollDepth < 0 || payload.scrollDepth > 100) {
                errors.add("Scroll depth must be between 0 and 100")
            }
        } else if (payload.scrollDepth != null && (payload.scrollDepth < 0 || payload.scrollDepth > 100)) {
            errors.add("Scroll depth must be between 0 and 100")
        }

        // Validate targetUrl (required for outbound/download events)
        if (payload.type in setOf("outbound", "download")) {
            if (payload.targetUrl.isNullOrBlank()) {
                errors.add("Target URL is required for ${payload.type} events")
            } else if (payload.targetUrl.length > MAX_TARGET_URL_LENGTH) {
                errors.add("Target URL exceeds maximum length of $MAX_TARGET_URL_LENGTH characters")
            }
        } else if (payload.targetUrl != null && payload.targetUrl.length > MAX_TARGET_URL_LENGTH) {
            errors.add("Target URL exceeds maximum length of $MAX_TARGET_URL_LENGTH characters")
        }

        // Validate properties (optional JSON string)
        payload.properties?.let { props ->
            if (props.length > MAX_PROPERTIES_LENGTH) {
                errors.add("Properties exceeds maximum length of $MAX_PROPERTIES_LENGTH characters")
            }
        }

        return if (errors.isEmpty()) {
            ValidationResult.success()
        } else {
            ValidationResult.failure(errors)
        }
    }

    private fun validateUtmField(value: String?, fieldName: String, errors: MutableList<String>) {
        value?.let {
            if (it.length > MAX_UTM_LENGTH) {
                errors.add("$fieldName exceeds maximum length of $MAX_UTM_LENGTH characters")
            }
            if (it.isNotEmpty() && !UTM_REGEX.matches(it)) {
                errors.add("$fieldName contains invalid characters")
            }
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
