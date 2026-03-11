package se.onemanstudio.api.models.admin

import kotlinx.serialization.Serializable

/**
 * Password reset request. Requires both the current password and the
 * server salt for verification — the current password authenticates
 * the caller, while the server salt proves server-operator access.
 */
@Serializable
data class PasswordResetRequest(
    val serverSalt: String,
    val currentPassword: String,
    val newPassword: String
)
