package se.onemanstudio.models

import kotlinx.serialization.Serializable

@Serializable
data class ContributionDay(
    val date: String,              // ISO date "2024-01-15"
    val visits: Long,
    val uniqueVisitors: Long,
    val level: Int                 // 0-4 intensity level
)

@Serializable
data class ContributionCalendar(
    val days: List<ContributionDay>,
    val maxVisits: Long,           // For calculating intensity levels
    val startDate: String,
    val endDate: String
)
