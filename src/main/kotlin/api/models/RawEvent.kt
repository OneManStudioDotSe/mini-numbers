package se.onemanstudio.api.models

import kotlinx.serialization.Serializable

@Serializable
data class RawEvent(
    val id: Long,
    val timestamp: String,
    val eventType: String,
    val eventName: String? = null,
    val path: String,
    val referrer: String?,
    val country: String?,
    val city: String?,
    val browser: String?,
    val os: String?,
    val device: String?,
    val sessionId: String,
    val duration: Int
)
