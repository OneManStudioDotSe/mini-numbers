package se.onemanstudio.api.models.collection

import kotlinx.serialization.Serializable

@Serializable
data class PageViewPayload(
    val path: String,
    val referrer: String? = null,
    val sessionId: String,
    val type: String,
    val eventName: String? = null,
    val browser: String? = null,
    val os: String? = null,
    val device: String? = null,
    // UTM campaign tracking
    val utmSource: String? = null,
    val utmMedium: String? = null,
    val utmCampaign: String? = null,
    val utmTerm: String? = null,
    val utmContent: String? = null,
    // Scroll depth (0-100 percentage)
    val scrollDepth: Int? = null,
    // Target URL for outbound links / file downloads
    val targetUrl: String? = null,
    // Custom event properties as JSON string
    val properties: String? = null
)
