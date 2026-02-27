package se.onemanstudio.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

/**
 * Scheduled email report configurations.
 * Each row represents a recurring report for a project sent to a specific email.
 */
object EmailReports : Table("email_reports") {
    val id = uuid("id")
    val projectId = uuid("project_id").references(Projects.id)
    val recipientEmail = varchar("recipient_email", 320)
    val schedule = varchar("schedule", 20).default("WEEKLY")   // DAILY, WEEKLY, MONTHLY
    val sendHour = integer("send_hour").default(8)              // 0-23, hour of day to send
    val sendDay = integer("send_day").default(1)                // 1=Monday (weekly), 1-28 (monthly)
    val timezone = varchar("timezone", 50).default("UTC")
    val subjectTemplate = varchar("subject_template", 500).default("{project_name} Analytics — {period}")
    val headerText = varchar("header_text", 500).nullable()
    val footerText = varchar("footer_text", 500).nullable()
    val includeSections = varchar("include_sections", 500).default("overview,pages,referrers,geo,events")
    val isActive = bool("is_active").default(true)
    val lastSentAt = datetime("last_sent_at").nullable()
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }

    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_email_reports_project", false, projectId)
        index("idx_email_reports_schedule", false, schedule, isActive)
    }
}
