package se.onemanstudio.api.models.dashboard

import kotlinx.serialization.Serializable

@Serializable
data class ContributionDay(
    val date: String, // ISO date "2024-01-15"
    val visits: Long,
    val uniqueVisitors: Long,
    val level: Int // 0-4 intensity level
)
