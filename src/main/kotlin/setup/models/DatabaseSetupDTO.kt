package se.onemanstudio.setup.models

import kotlinx.serialization.Serializable

/**
 * Database configuration for setup wizard
 */
@Serializable
data class DatabaseSetupDTO(
    val type: String,  // "SQLITE" or "POSTGRESQL"
    val sqlitePath: String? = null,
    val pgHost: String? = null,
    val pgPort: Int? = null,
    val pgName: String? = null,
    val pgUsername: String? = null,
    val pgPassword: String? = null,
    val pgMaxPoolSize: Int? = null
)
