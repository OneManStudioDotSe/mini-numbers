package se.onemanstudio.api.models.widget

import kotlinx.serialization.Serializable

/** Widget 1: Real-time visitor counter */
@Serializable
data class RealtimeCounterWidget(
    val activeVisitors: Long,
    val timestamp: String
)

/** Widget 2: Page view counter */
@Serializable
data class PageViewWidget(
    val views: Long,
    val scope: String,
    val filter: String,
    val path: String? = null
)

/** Widget 3: Top pages list */
@Serializable
data class TopPagesWidget(
    val pages: List<TopPageEntry>,
    val filter: String
)

@Serializable
data class TopPageEntry(
    val path: String,
    val views: Long
)

/** Widget 4: Visitor sparkline (last 7 days) */
@Serializable
data class SparklineWidget(
    val points: List<SparklinePoint>,
    val maxValue: Long
)

@Serializable
data class SparklinePoint(
    val date: String,
    val views: Long
)
