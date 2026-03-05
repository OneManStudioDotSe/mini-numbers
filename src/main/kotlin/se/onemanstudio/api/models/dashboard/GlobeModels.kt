package se.onemanstudio.api.models.dashboard

import kotlinx.serialization.Serializable

@Serializable
data class GlobeVisitor(
    val lat: Double,
    val lng: Double,
    val city: String?,
    val country: String?,
    val count: Long,
    val lastSeen: String
)

@Serializable
data class GlobeData(
    val visitors: List<GlobeVisitor>,
    val totalActive: Long
)
