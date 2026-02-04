package se.onemanstudio.models

import kotlinx.serialization.Serializable

@Serializable
data class ProjectStats(
    val totalViews: Long,
    val uniqueVisitors: Long,
    val topPages: List<TopPage>
)
