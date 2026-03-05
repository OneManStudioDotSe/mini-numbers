package se.onemanstudio.api.models

import kotlinx.serialization.Serializable

/**
 * Standardized API error response
 * All error responses follow this format for consistency
 */
@Serializable
data class ApiError(
    val error: String,
    val code: String,
    val details: List<String> = emptyList()
) {
    companion object {
        fun badRequest(message: String, details: List<String> = emptyList()) =
            ApiError(error = message, code = "BAD_REQUEST", details = details)

        fun notFound(message: String) =
            ApiError(error = message, code = "NOT_FOUND")

        fun unauthorized(message: String) =
            ApiError(error = message, code = "UNAUTHORIZED")

        fun rateLimited(message: String, limitType: String, limit: Int, window: String) =
            ApiError(
                error = message,
                code = "RATE_LIMITED",
                details = listOf("limit_type=$limitType", "limit=$limit", "window=$window")
            )

        fun internalError(message: String) =
            ApiError(error = message, code = "INTERNAL_ERROR")

        fun validationFailed(errors: List<String>) =
            ApiError(error = "Validation failed", code = "VALIDATION_ERROR", details = errors)

        fun serviceUnavailable(message: String) =
            ApiError(error = message, code = "SERVICE_UNAVAILABLE")
    }
}
