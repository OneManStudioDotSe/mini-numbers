package se.onemanstudio.api.models.dashboard

import kotlinx.serialization.Serializable

@Serializable
data class TopPage(
    val path: String,
    val count: Long
)
