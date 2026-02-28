package se.onemanstudio.api.models.dashboard

import kotlinx.serialization.Serializable

@Serializable
data class RawEventsResponse(
    val events: List<RawEvent>,
    val total: Long,
    val page: Int,
    val limit: Int
)
