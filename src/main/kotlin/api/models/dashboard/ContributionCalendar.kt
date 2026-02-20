package se.onemanstudio.api.models.dashboard

import kotlinx.serialization.Serializable

@Serializable
data class ContributionCalendar(
    val days: List<ContributionDay>,
    val maxVisits: Long, // For calculating intensity levels
    val startDate: String,
    val endDate: String
)
