package se.onemanstudio.core.models

import kotlinx.serialization.Serializable

/**
 * User session data
 */
@Serializable
data class UserSession(
    val username: String,
    val role: String = "admin",
    val createdAt: Long = System.currentTimeMillis()
)
