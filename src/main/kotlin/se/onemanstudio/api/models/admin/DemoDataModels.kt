package se.onemanstudio.api.models.admin

import kotlinx.serialization.Serializable

/**
 * Request payload for demo data generation
 */
@Serializable
data class DemoDataRequest(
    val count: Int = 500,
    val timeScope: Int = 30
)
