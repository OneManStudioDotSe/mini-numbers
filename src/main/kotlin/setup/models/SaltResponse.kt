package se.onemanstudio.setup.models

import kotlinx.serialization.Serializable

/**
 * Salt generation response
 */
@Serializable
data class SaltResponse(
    val salt: String
)
