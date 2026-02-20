package se.onemanstudio.api.models.dashboard

import kotlinx.serialization.Serializable

/**
 * Time series data point for trend visualization
 * Represents aggregated views and visitors for a specific time bucket
 */
@Serializable
data class TimeSeriesPoint(
    val timestamp: String, // ISO 8601 datetime string
    val views: Long, // Total views in this time bucket
    val uniqueVisitors: Long // Unique visitors in this time bucket
)
