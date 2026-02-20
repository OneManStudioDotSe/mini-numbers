package se.onemanstudio.api.models.dashboard

import kotlinx.serialization.Serializable
import se.onemanstudio.api.models.StatEntry

@Serializable
data class PeakTimeAnalysis(
    val topHours: List<StatEntry>, // Top 5 hours by traffic
    val topDays: List<StatEntry>, // Top 3 days by traffic
    val peakHour: Int, // Single peak hour (0-23)
    val peakDay: Int // Single peak day (0-6)
)