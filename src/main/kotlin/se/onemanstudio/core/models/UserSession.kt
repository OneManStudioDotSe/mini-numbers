package se.onemanstudio.core.models

import kotlinx.serialization.Serializable

/**
 * User session data, stored in the signed cookie.
 *
 * No property has a default on purpose: the session serializer omits properties left at
 * their default, and a `createdAt` that is re-defaulted to "now" on every read would defeat
 * the 4-hour lifetime and the logout cut-off.
 */
@Serializable
data class UserSession(
    val username: String,
    val role: String,
    val createdAt: Long
)
