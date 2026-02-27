package se.onemanstudio.api.models

import kotlinx.serialization.Serializable

@Serializable
data class CreateEmailReportRequest(
    val recipientEmail: String,
    val schedule: String = "WEEKLY",
    val sendHour: Int = 8,
    val sendDay: Int = 1,
    val timezone: String = "UTC",
    val subjectTemplate: String = "{project_name} Analytics — {period}",
    val headerText: String? = null,
    val footerText: String? = null,
    val includeSections: List<String> = listOf("overview", "pages", "referrers", "geo", "events")
)

@Serializable
data class UpdateEmailReportRequest(
    val isActive: Boolean? = null,
    val schedule: String? = null,
    val sendHour: Int? = null,
    val sendDay: Int? = null,
    val timezone: String? = null,
    val subjectTemplate: String? = null,
    val headerText: String? = null,
    val footerText: String? = null,
    val includeSections: List<String>? = null
)

@Serializable
data class EmailReportResponse(
    val id: String,
    val projectId: String,
    val recipientEmail: String,
    val schedule: String,
    val sendHour: Int,
    val sendDay: Int,
    val timezone: String,
    val subjectTemplate: String,
    val headerText: String? = null,
    val footerText: String? = null,
    val includeSections: List<String>,
    val isActive: Boolean,
    val lastSentAt: String? = null,
    val createdAt: String
)

@Serializable
data class SmtpStatusResponse(
    val configured: Boolean,
    val host: String? = null,
    val port: Int? = null,
    val from: String? = null
)
