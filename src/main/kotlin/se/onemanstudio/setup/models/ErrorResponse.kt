package se.onemanstudio.setup.models

import kotlinx.serialization.Serializable

/**
 * Error response
 */
@Serializable
data class ErrorResponse(
    val error: String
)
