package se.onemanstudio.api.models

import kotlinx.serialization.Serializable

@Serializable
data class SegmentFilter(
    val field: String,     // browser, os, device, country, path, referrer, eventType
    val operator: String,  // equals, not_equals, contains, starts_with
    val value: String,
    val logic: String = "AND" // AND or OR (for combining with next filter)
)

@Serializable
data class SegmentRequest(
    val name: String,
    val description: String? = null,
    val filters: List<SegmentFilter>
)

@Serializable
data class SegmentResponse(
    val id: String,
    val name: String,
    val description: String?,
    val filters: List<SegmentFilter>,
    val createdAt: String
)

@Serializable
data class SegmentAnalysis(
    val segmentId: String,
    val segmentName: String,
    val totalViews: Long,
    val uniqueVisitors: Long,
    val bounceRate: Double,
    val topPages: List<StatEntry>,
    val matchingEvents: Long
)
