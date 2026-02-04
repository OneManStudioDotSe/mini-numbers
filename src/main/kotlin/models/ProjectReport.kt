package se.onemanstudio.models

import kotlinx.serialization.Serializable

@Serializable
data class ProjectReport(
    val totalViews: Long,
    val uniqueVisitors: Long,
    val topPages: List<StatEntry>,
    val browsers: List<StatEntry>,
    val oss: List<StatEntry>,
    val devices: List<StatEntry>,
    val referrers: List<StatEntry>,
    val countries: List<StatEntry>,
    val lastVisits: List<VisitSnippet>,
    val activityHeatmap: List<ActivityCell>,     // 7x24 heatmap for traffic visualization
    val peakTimeAnalysis: PeakTimeAnalysis      // Peak hours and days analysis
)
