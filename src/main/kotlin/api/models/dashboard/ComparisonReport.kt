package se.onemanstudio.api.models.dashboard

import kotlinx.serialization.Serializable
import se.onemanstudio.api.models.ProjectReport

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
