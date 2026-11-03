package se.onemanstudio.services

import jakarta.mail.*
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import se.onemanstudio.api.models.dashboard.ProjectReport
import se.onemanstudio.config.models.EmailConfig
import se.onemanstudio.db.EmailReports
import se.onemanstudio.db.Projects
import se.onemanstudio.utils.generateReport
import se.onemanstudio.utils.getCurrentPeriod
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.*
import java.util.concurrent.Executors

/**
 * Email report service — generates and sends HTML analytics reports via SMTP.
 * Uses jakarta.mail for SMTP delivery.
 */
object EmailService {
    private val logger = LoggerFactory.getLogger(EmailService::class.java)
    private val executor = Executors.newSingleThreadExecutor()

    @Volatile
    private var emailConfig: EmailConfig? = null

    private var reportTimer: Timer? = null

    fun init(config: EmailConfig?) {
        this.emailConfig = config
        if (config?.isConfigured() == true) {
            logger.info("Email service initialized (host: ${config.smtpHost}, from: ${config.smtpFrom})")
        } else {
            logger.info("Email service not configured — email reports disabled")
        }
    }

    fun isConfigured(): Boolean = emailConfig?.isConfigured() == true

    fun getSmtpStatus(): Map<String, Any?> {
        val cfg = emailConfig
        return mapOf(
            "configured" to (cfg?.isConfigured() == true),
            "host" to cfg?.smtpHost,
            "port" to cfg?.smtpPort,
            "from" to cfg?.smtpFrom
        )
    }

    /**
     * Send a report email asynchronously.
     * @param recordSend false for a manual test send, so it does not count as that day's scheduled send
     */
    fun sendReportAsync(
        projectId: UUID,
        recipientEmail: String,
        period: String,
        reportId: UUID? = null,
        recordSend: Boolean = true
    ) {
        executor.submit {
            try {
                sendReport(projectId, recipientEmail, period, reportId, recordSend)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                logger.error("Failed to send email report to $recipientEmail: ${e.message}")
            }
        }
    }

    /**
     * Generate and send an HTML analytics report via SMTP.
     */
    @Suppress("LongParameterList")
    fun sendReport(
        projectId: UUID,
        recipientEmail: String,
        period: String,
        reportId: UUID? = null,
        recordSend: Boolean = true
    ) {
        val config = checkNotNull(emailConfig) { "SMTP not configured" }
        check(config.isConfigured()) { "SMTP not configured" }

        // Get project info
        val project = transaction {
            Projects.selectAll().where { Projects.id eq projectId }.singleOrNull()
        } ?: throw IllegalArgumentException("Project not found: $projectId")

        val projectName = project[Projects.name]
        val projectDomain = project[Projects.domain]

        // Get report config for template customization
        val reportConfig = if (reportId != null) {
            transaction {
                EmailReports.selectAll().where { EmailReports.id eq reportId }.singleOrNull()
            }
        } else null

        // Generate report data
        val (start, end) = getCurrentPeriod(period)
        val report = generateReport(projectId, start, end)

        // Build subject
        val subjectTemplate = reportConfig?.get(EmailReports.subjectTemplate)
            ?: "{project_name} Analytics — {period}"
        val subject = subjectTemplate
            .replace("{project_name}", projectName)
            .replace("{period}", period)
            .replace("{date}", LocalDateTime.now().toLocalDate().toString())

        val headerText = reportConfig?.get(EmailReports.headerText)
        val footerText = reportConfig?.get(EmailReports.footerText)
        val sections = reportConfig?.get(EmailReports.includeSections)
            ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
            ?: ALL_SECTIONS

        // Build HTML email
        val html = buildReportHtml(projectName, projectDomain, period, report, headerText, footerText, sections)

        // Send via SMTP
        val props = Properties().apply {
            put("mail.smtp.host", checkNotNull(config.smtpHost) { "SMTP host is required" })
            put("mail.smtp.port", config.smtpPort.toString())
            put("mail.smtp.auth", (!config.smtpUsername.isNullOrBlank()).toString())
            if (config.smtpStartTls) {
                put("mail.smtp.starttls.enable", "true")
            }
        }

        val session = if (!config.smtpUsername.isNullOrBlank()) {
            Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(config.smtpUsername, config.smtpPassword ?: "")
                }
            })
        } else {
            Session.getInstance(props)
        }

        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(config.smtpFrom))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail))
            setSubject(subject, "UTF-8")
            setContent(html, "text/html; charset=UTF-8")
            sentDate = Date()
        }

        Transport.send(message)
        logger.info("Email report sent to $recipientEmail for project $projectName ($period)")

        // Update lastSentAt (not for a manual test send, which must not replace the scheduled one)
        if (reportId != null && recordSend) {
            transaction {
                EmailReports.update({ EmailReports.id eq reportId }) {
                    it[lastSentAt] = LocalDateTime.now()
                }
            }
        }
    }

    /**
     * Start the report scheduler that checks every hour for due reports.
     */
    fun startScheduler() {
        stopScheduler()
        if (!isConfigured()) return

        reportTimer = Timer("email-reports", true).apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    try {
                        checkAndSendDueReports()
                    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                        logger.error("Email report scheduler error: ${e.message}")
                    }
                }
            }, 60_000L, 60 * 60 * 1000L) // Start after 1 min, check every hour
        }
        logger.info("Email report scheduler started (checking every hour)")
    }

    fun stopScheduler() {
        reportTimer?.cancel()
        reportTimer = null
    }

    private fun checkAndSendDueReports() {
        val dueReports = transaction {
            EmailReports.selectAll().where {
                EmailReports.isActive eq true
            }.toList()
        }

        for (report in dueReports) {
            val schedule = report[EmailReports.schedule]
            // "Send at 8" means 8 o'clock in the report's own timezone, not the server's
            val zone = runCatching { ZoneId.of(report[EmailReports.timezone]) }.getOrDefault(ZoneOffset.UTC)
            val now = ZonedDateTime.now(zone)
            val lastSentDate = report[EmailReports.lastSentAt]
                ?.atZone(ZoneId.systemDefault())?.withZoneSameInstant(zone)?.toLocalDate()

            if (isDue(schedule, report[EmailReports.sendHour], report[EmailReports.sendDay], lastSentDate, now)) {
                val period = when (schedule) {
                    "DAILY" -> "24h"
                    "WEEKLY" -> "7d"
                    "MONTHLY" -> "30d"
                    else -> "7d"
                }

                try {
                    sendReport(
                        projectId = report[EmailReports.projectId],
                        recipientEmail = report[EmailReports.recipientEmail],
                        period = period,
                        reportId = report[EmailReports.id]
                    )
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    logger.error("Failed to send scheduled report ${report[EmailReports.id]}: ${e.message}")
                }
            }
        }
    }

    /** Section names accepted in `includeSections`. */
    val ALL_SECTIONS = setOf("overview", "pages", "referrers", "geo", "events")

    /**
     * Whether a report is due at [now] (already in the report's timezone): the right hour,
     * the right day for weekly/monthly schedules, and not already sent on that date.
     */
    internal fun isDue(schedule: String, sendHour: Int, sendDay: Int, lastSentDate: LocalDate?, now: ZonedDateTime): Boolean {
        val notSentToday = lastSentDate != now.toLocalDate()
        return when (schedule) {
            "DAILY" -> now.hour == sendHour && notSentToday
            "WEEKLY" -> now.hour == sendHour && now.dayOfWeek.value == sendDay && notSentToday // 1 = Monday
            "MONTHLY" -> now.hour == sendHour && now.dayOfMonth == sendDay && notSentToday
            else -> false
        }
    }

    /**
     * Build HTML email report with inline CSS (email-safe). Everything that comes from the
     * database or the request is escaped; [sections] picks which blocks are rendered.
     */
    @Suppress("LongMethod", "LongParameterList")
    internal fun buildReportHtml(
        projectName: String,
        projectDomain: String,
        period: String,
        report: ProjectReport,
        headerText: String?,
        footerText: String?,
        sections: Set<String> = ALL_SECTIONS
    ): String {
        val periodLabel = when (period) {
            "24h" -> "Last 24 hours"
            "3d" -> "Last 3 days"
            "7d" -> "Last 7 days"
            "30d" -> "Last 30 days"
            "365d" -> "Last year"
            else -> period
        }

        return """
        <!DOCTYPE html>
        <html lang="en">
        <head><meta charset="UTF-8"></head>
        <body style="margin:0;padding:0;background:#f4f5f7;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
        <div style="max-width:600px;margin:0 auto;padding:20px;">
            <div style="background:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 1px 3px rgba(0,0,0,0.1);">
                <!-- Header -->
                <div style="background:linear-gradient(135deg,#6366f1,#8b5cf6);padding:32px 24px;color:#ffffff;">
                    <div style="font-size:24px;font-weight:700;margin-bottom:4px;">${esc(projectName)}</div>
                    <div style="font-size:14px;opacity:0.85;">$periodLabel &middot; ${esc(projectDomain)}</div>
                    ${if (!headerText.isNullOrBlank()) "<div style=\"margin-top:12px;font-size:14px;opacity:0.9;\">${esc(headerText)}</div>" else ""}
                </div>

                <!-- Stats -->
                ${if ("overview" in sections) """
                <div style="padding:24px;">
                    <table style="width:100%;border-collapse:collapse;">
                        <tr>
                            <td style="text-align:center;padding:16px 8px;">
                                <div style="font-size:28px;font-weight:700;color:#1a1a2e;">${report.totalViews}</div>
                                <div style="font-size:12px;color:#6b7280;margin-top:4px;">Page Views</div>
                            </td>
                            <td style="text-align:center;padding:16px 8px;">
                                <div style="font-size:28px;font-weight:700;color:#1a1a2e;">${report.uniqueVisitors}</div>
                                <div style="font-size:12px;color:#6b7280;margin-top:4px;">Unique Visitors</div>
                            </td>
                            <td style="text-align:center;padding:16px 8px;">
                                <div style="font-size:28px;font-weight:700;color:#1a1a2e;">${"%.1f".format(report.bounceRate)}%</div>
                                <div style="font-size:12px;color:#6b7280;margin-top:4px;">Bounce Rate</div>
                            </td>
                        </tr>
                    </table>
                </div>""" else ""}

                ${if ("pages" in sections) topTable("Top Pages", report.topPages) else ""}
                ${if ("referrers" in sections) topTable("Top Referrers", report.referrers) else ""}
                ${if ("geo" in sections) topTable("Top Countries", report.countries) else ""}
                ${if ("events" in sections) topTable("Top Events", report.customEvents) else ""}

                <!-- Footer -->
                <div style="padding:16px 24px;background:#f9fafb;border-top:1px solid #f0f0f0;text-align:center;">
                    ${if (!footerText.isNullOrBlank()) "<div style=\"font-size:13px;color:#6b7280;margin-bottom:8px;\">${esc(footerText)}</div>" else ""}
                    <div style="font-size:12px;color:#9ca3af;">
                        Sent by Mini Numbers &middot; Privacy-first web analytics
                    </div>
                </div>
            </div>
        </div>
        </body>
        </html>
        """.trimIndent()
    }
}

/** Escape text that lands in the email HTML. */
private fun esc(text: String): String = text
    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

/** One "Top …" table of label/count rows, or nothing when there is no data. */
private fun topTable(title: String, rows: List<se.onemanstudio.api.models.StatEntry>): String {
    if (rows.isEmpty()) return ""
    val body = rows.take(5).joinToString("") { row ->
        """<tr style="border-bottom:1px solid #f0f0f0;">
                            <td style="padding:8px 0;color:#374151;">${esc(row.label)}</td>
                            <td style="padding:8px 0;text-align:right;color:#6366f1;font-weight:500;">${row.value}</td>
                        </tr>"""
    }
    return """
            <div style="padding:0 24px 24px;">
                <div style="font-size:16px;font-weight:600;color:#1a1a2e;margin-bottom:12px;">$title</div>
                <table style="width:100%;border-collapse:collapse;font-size:13px;">
                    $body
                </table>
            </div>"""
}
