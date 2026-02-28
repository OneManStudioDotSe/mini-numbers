package se.onemanstudio.api.models.admin

import kotlinx.serialization.Serializable

@Serializable
data class FunnelStepRequest(
    val name: String,
    val stepType: String,
    val matchValue: String
)

@Serializable
data class FunnelRequest(
    val name: String,
    val steps: List<FunnelStepRequest>
)

@Serializable
data class FunnelResponse(
    val id: String,
    val name: String,
    val steps: List<FunnelStepResponse>,
    val createdAt: String
)

@Serializable
data class FunnelStepResponse(
    val id: String,
    val stepNumber: Int,
    val name: String,
    val stepType: String,
    val matchValue: String
)

@Serializable
data class FunnelAnalysis(
    val funnel: FunnelResponse,
    val totalSessions: Long,
    val steps: List<FunnelStepAnalysis>
)

@Serializable
data class FunnelStepAnalysis(
    val stepNumber: Int,
    val name: String,
    val sessions: Long,
    val conversionRate: Double,
    val dropOffRate: Double,
    val avgTimeFromPrevious: Double? = null
)
