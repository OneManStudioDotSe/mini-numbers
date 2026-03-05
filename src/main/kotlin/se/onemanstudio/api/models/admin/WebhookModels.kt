package se.onemanstudio.api.models.admin

import kotlinx.serialization.Serializable

@Serializable
data class CreateWebhookRequest(
    val url: String,
    val events: List<String> = listOf("goal_conversion", "traffic_spike")
)

@Serializable
data class WebhookResponse(
    val id: String,
    val projectId: String,
    val url: String,
    val events: List<String>,
    val isActive: Boolean,
    val createdAt: String
)

@Serializable
data class WebhookDeliveryResponse(
    val id: String,
    val eventType: String,
    val responseCode: Int? = null,
    val attempt: Int,
    val status: String,
    val createdAt: String,
    val deliveredAt: String? = null
)
