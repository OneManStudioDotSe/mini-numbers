package se.onemanstudio.setup.models

import kotlinx.serialization.Serializable

/**
 * Configuration save response
 */
@Serializable
data class SaveResponse(
    val success: Boolean,
    val message: String
)
