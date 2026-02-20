package se.onemanstudio.api.models.dashboard

import kotlinx.serialization.Serializable

/**
 * Activity cell for heatmap visualization
 * Represents the number of visits for a specific hour on a specific day of week
 */
@Serializable
data class ActivityCell(
    val dayOfWeek: Int, // 0=Sunday, 1=Monday, ..., 6=Saturday
    val hourOfDay: Int, // 0-23 (24-hour format)
    val count: Long // Number of visits for this hour/day combination
)