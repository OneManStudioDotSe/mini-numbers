package se.onemanstudio.api.models

import kotlinx.serialization.Serializable
import se.onemanstudio.api.models.dashboard.TopPage

@Serializable
data class ProjectStats(
    val totalViews: Long,
    val uniqueVisitors: Long,
    val topPages: List<TopPage>
)
