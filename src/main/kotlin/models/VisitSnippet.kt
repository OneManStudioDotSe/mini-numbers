package se.onemanstudio.models

import kotlinx.serialization.Serializable

@Serializable
data class VisitSnippet(
    val path: String,
    val timestamp: String,
    val city: String?
)
