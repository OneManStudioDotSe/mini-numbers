package se.onemanstudio.api.models

import kotlinx.serialization.Serializable

@Serializable
data class StatEntry(
    val label: String,
    val value: Long
)
