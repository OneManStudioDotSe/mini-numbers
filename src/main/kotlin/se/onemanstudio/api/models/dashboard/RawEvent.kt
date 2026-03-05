package se.onemanstudio.api.models.dashboard

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
    val duration: Int,
    val utmSource: String? = null,
    val utmCampaign: String? = null,
    val scrollDepth: Int? = null,
    val region: String? = null,
    val targetUrl: String? = null,
    val properties: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)
