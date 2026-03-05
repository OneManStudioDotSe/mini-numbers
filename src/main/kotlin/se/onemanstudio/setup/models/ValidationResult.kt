package se.onemanstudio.setup.models

import kotlinx.serialization.Serializable

/**
 * Validation result with detailed error messages
 */
@Serializable
data class ValidationResult(
    val valid: Boolean,
    val errors: Map<String, String>
)
