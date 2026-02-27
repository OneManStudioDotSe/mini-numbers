package se.onemanstudio.api.models

import kotlinx.serialization.Serializable

@Serializable
data class CreateUserRequest(
    val username: String,
    val password: String,
    val role: String = "viewer"
)

@Serializable
data class UpdateUserRoleRequest(
    val role: String
)

@Serializable
data class UserResponse(
    val id: String,
    val username: String,
    val role: String,
    val isActive: Boolean,
    val createdAt: String
)
