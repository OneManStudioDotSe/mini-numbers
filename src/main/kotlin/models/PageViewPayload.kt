package se.onemanstudio.models

import kotlinx.serialization.Serializable

@Serializable
data class PageViewPayload(
    val path: String,
    val referrer: String? = null,
    val sessionId: String,
    val type: String,
    val browser: String? = null, // New
    val os: String? = null,      // New
    val device: String? = null   // New
)