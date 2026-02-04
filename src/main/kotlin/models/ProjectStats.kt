package se.onemanstudio.models

import kotlinx.serialization.Serializable
import se.onemanstudio.models.dashboard.TopPage

@Serializable
data class ProjectStats(
    val totalViews: Long,
    val uniqueVisitors: Long,
    val topPages: List<TopPage>
)
