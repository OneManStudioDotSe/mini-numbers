package se.onemanstudio.api.models

import kotlinx.serialization.Serializable
import se.onemanstudio.api.models.dashboard.ActivityCell
import se.onemanstudio.api.models.dashboard.PeakTimeAnalysis

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
    val customEvents: List<StatEntry>,
    val lastVisits: List<VisitSnippet>,
    val activityHeatmap: List<ActivityCell>,
    val peakTimeAnalysis: PeakTimeAnalysis,
    val bounceRate: Double, // Percentage of single-page sessions with no heartbeat
    // UTM campaign tracking
    val utmSources: List<StatEntry> = emptyList(),
    val utmMediums: List<StatEntry> = emptyList(),
    val utmCampaigns: List<StatEntry> = emptyList(),
    // Scroll depth distribution
    val scrollDepthDistribution: List<StatEntry> = emptyList(),
    // Session metrics
    val totalSessions: Long = 0,
    val avgSessionDuration: Double = 0.0,
    // Entry and exit pages
    val entryPages: List<StatEntry> = emptyList(),
    val exitPages: List<StatEntry> = emptyList(),
    // Outbound links and file downloads
    val outboundLinks: List<StatEntry> = emptyList(),
    val fileDownloads: List<StatEntry> = emptyList(),
    // Region/state geography
    val regions: List<StatEntry> = emptyList(),
    // Overall conversion rate
    val conversionRate: Double = 0.0
)
