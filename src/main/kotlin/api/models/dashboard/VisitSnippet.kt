package se.onemanstudio.api.models.dashboard

import kotlinx.serialization.Serializable

@Serializable
data class VisitSnippet(
    val path: String,
    val timestamp: String,
    val city: String?,
    val country: String? = null
)
