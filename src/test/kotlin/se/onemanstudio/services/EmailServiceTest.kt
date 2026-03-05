package se.onemanstudio.services

import org.junit.Test
import se.onemanstudio.config.models.EmailConfig
import kotlin.test.*

/**
 * Unit tests for EmailService configuration and status
 */
class EmailServiceTest {

    @Test
    fun `EmailConfig isConfigured returns true when host and from are set`() {
        val config = EmailConfig(
            smtpHost = "smtp.example.com",
            smtpPort = 587,
            smtpFrom = "test@example.com"
        )
        assertTrue(config.isConfigured())
    }

    @Test
    fun `EmailConfig isConfigured returns false when host is null`() {
        val config = EmailConfig(
            smtpHost = null,
            smtpFrom = "test@example.com"
        )
        assertFalse(config.isConfigured())
    }

    @Test
    fun `EmailConfig isConfigured returns false when host is blank`() {
        val config = EmailConfig(
            smtpHost = "  ",
            smtpFrom = "test@example.com"
        )
        assertFalse(config.isConfigured())
    }

    @Test
    fun `EmailConfig isConfigured returns false when from is null`() {
        val config = EmailConfig(
            smtpHost = "smtp.example.com",
            smtpFrom = null
        )
        assertFalse(config.isConfigured())
    }

    @Test
    fun `EmailConfig isConfigured returns false when from is blank`() {
        val config = EmailConfig(
            smtpHost = "smtp.example.com",
            smtpFrom = ""
        )
        assertFalse(config.isConfigured())
    }

    @Test
    fun `EmailConfig defaults are correct`() {
        val config = EmailConfig()
        assertEquals(587, config.smtpPort)
        assertTrue(config.smtpStartTls)
        assertNull(config.smtpHost)
        assertNull(config.smtpUsername)
        assertNull(config.smtpPassword)
        assertNull(config.smtpFrom)
    }

    @Test
    fun `EmailService getSmtpStatus returns not configured when no config`() {
        EmailService.init(null)
        val status = EmailService.getSmtpStatus()
        assertEquals(false, status["configured"])
        assertNull(status["host"])
    }

    @Test
    fun `EmailService getSmtpStatus returns configured when SMTP set up`() {
        val config = EmailConfig(
            smtpHost = "smtp.test.com",
            smtpPort = 465,
            smtpFrom = "reports@test.com"
        )
        EmailService.init(config)
        val status = EmailService.getSmtpStatus()
        assertEquals(true, status["configured"])
        assertEquals("smtp.test.com", status["host"])
        assertEquals(465, status["port"])
        assertEquals("reports@test.com", status["from"])
    }

    @Test
    fun `EmailService isConfigured returns false with empty config`() {
        EmailService.init(EmailConfig())
        assertFalse(EmailService.isConfigured())
    }

    @Test
    fun `EmailService isConfigured returns true with valid config`() {
        EmailService.init(EmailConfig(
            smtpHost = "smtp.test.com",
            smtpFrom = "test@test.com"
        ))
        assertTrue(EmailService.isConfigured())
    }
}
