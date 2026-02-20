package se.onemanstudio.setup.models

import kotlinx.serialization.Serializable

/**
 * Main setup configuration DTO received from the setup wizard
 */
@Serializable
data class SetupConfigDTO(
    val adminUsername: String,
    val adminPassword: String,
    val serverSalt: String,
    val allowedOrigins: String,
    val database: DatabaseSetupDTO,
    val server: ServerSetupDTO,
    val geoip: GeoIPSetupDTO,
    val rateLimit: RateLimitSetupDTO
)
