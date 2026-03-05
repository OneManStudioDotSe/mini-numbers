package se.onemanstudio.api.models.admin

import kotlinx.serialization.Serializable

@Serializable
data class GoalRequest(
    val name: String,
    val goalType: String,
    val matchValue: String
)

@Serializable
data class GoalResponse(
    val id: String,
    val name: String,
    val goalType: String,
    val matchValue: String,
    val isActive: Boolean,
    val createdAt: String
)

@Serializable
data class GoalStats(
    val goal: GoalResponse,
    val conversions: Long,
    val conversionRate: Double,
    val previousConversions: Long,
    val previousConversionRate: Double
)
