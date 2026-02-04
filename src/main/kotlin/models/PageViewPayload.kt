package se.onemanstudio.models

import kotlinx.serialization.Serializable

@Serializable
data class PageViewPayload(
    val path: String,
    val referrer: String? = null,
    val sessionId: String,
    val type: String,
    val browser: String? = null,
    val os: String? = null,
    val device: String? = null
)
