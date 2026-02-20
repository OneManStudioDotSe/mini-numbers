package se.onemanstudio.setup.models

import kotlinx.serialization.Serializable

/**
 * GeoIP configuration for setup wizard
 */
@Serializable
data class GeoIPSetupDTO(
    val databasePath: String
)
