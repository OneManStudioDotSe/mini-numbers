package se.onemanstudio.models

import kotlinx.serialization.Serializable

/**
 * Comparison report containing current and previous period data
 * Used for period-over-period analysis in the analytics dashboard
 */
@Serializable
data class ComparisonReport(
    val current: ProjectReport,
    val previous: ProjectReport,
    val timeSeries: List<TimeSeriesPoint>
)

/**
 * Time series data point for trend visualization
 * Represents aggregated views and visitors for a specific time bucket
 */
@Serializable
data class TimeSeriesPoint(
    val timestamp: String,         // ISO 8601 datetime string
    val views: Long,               // Total views in this time bucket
    val uniqueVisitors: Long       // Unique visitors in this time bucket
)
