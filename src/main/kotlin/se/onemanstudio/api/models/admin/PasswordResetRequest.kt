package se.onemanstudio.api.models.admin

import kotlinx.serialization.Serializable

/**
 * Password reset request. Requires the server salt for verification
 * since only the server operator has access to it.
 */
@Serializable
data class PasswordResetRequest(
    val serverSalt: String,
    val newPassword: String
)
