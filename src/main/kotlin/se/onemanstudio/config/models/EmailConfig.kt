package se.onemanstudio.config.models

/**
 * Email/SMTP configuration for scheduled reports.
 * All fields are optional — email features are disabled when SMTP is not configured.
 */
data class EmailConfig(
    val smtpHost: String? = null,
    val smtpPort: Int = 587,
    val smtpUsername: String? = null,
    val smtpPassword: String? = null,
    val smtpFrom: String? = null,
    val smtpStartTls: Boolean = true
) {
    /** Returns true if all required SMTP fields are populated */
    fun isConfigured(): Boolean =
        !smtpHost.isNullOrBlank() && !smtpFrom.isNullOrBlank()
}
