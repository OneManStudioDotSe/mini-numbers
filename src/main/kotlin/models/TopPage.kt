package se.onemanstudio.models

import kotlinx.serialization.Serializable

@Serializable
data class TopPage(
    val path: String,
    val count: Long
)


