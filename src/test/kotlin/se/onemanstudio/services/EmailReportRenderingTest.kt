package se.onemanstudio.services

import org.junit.Test
import se.onemanstudio.api.models.StatEntry
import se.onemanstudio.api.models.dashboard.PeakTimeAnalysis
import se.onemanstudio.api.models.dashboard.ProjectReport
import se.onemanstudio.config.models.EmailConfig
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Scheduling and rendering of email reports (audit item M4). */
class EmailReportRenderingTest {

    private fun at(text: String, zone: String) = ZonedDateTime.of(java.time.LocalDateTime.parse(text), ZoneId.of(zone))

    private fun instant(text: String, zone: String = "UTC") = at(text, zone).toInstant()

    @Test
    fun `a daily report is due at its hour in its own timezone and only once per day`() {
        val stockholmEight = at("2026-11-03T08:30:00", "Europe/Stockholm") // 07:30 UTC
        assertTrue(EmailService.isDue("DAILY", 8, 1, null, stockholmEight))
        assertFalse(EmailService.isDue("DAILY", 8, 1, instant("2026-11-03T08:00:05", "Europe/Stockholm"), stockholmEight),
            "already sent today")
        assertTrue(EmailService.isDue("DAILY", 8, 1, instant("2026-11-02T08:00:05", "Europe/Stockholm"), stockholmEight),
            "sent yesterday")
        assertFalse(EmailService.isDue("DAILY", 8, 1, instant("2026-11-02T08:00:05", "UTC"),
            stockholmEight.withZoneSameInstant(ZoneId.of("UTC"))),
            "the same instant is 07:xx in UTC, so today's UTC 08:00 slot has not come yet")
    }

    @Test
    fun `weekly and monthly reports also need the right day`() {
        val tuesday3rd = at("2026-11-03T08:05:00", "UTC")
        assertTrue(EmailService.isDue("WEEKLY", 8, 2, null, tuesday3rd), "2 = Tuesday")
        assertTrue(EmailService.isDue("MONTHLY", 8, 3, null, tuesday3rd))
        // The slot of day 1 (Monday / the 1st) is in the past and has been sent: nothing owed today
        val sent = instant("2026-11-02T08:00:05")
        assertFalse(EmailService.isDue("WEEKLY", 8, 1, sent, tuesday3rd), "1 = Monday, sent")
        assertFalse(EmailService.isDue("MONTHLY", 8, 1, instant("2026-11-01T08:00:05"), tuesday3rd))
        assertFalse(EmailService.isDue("YEARLY", 8, 3, null, tuesday3rd), "unknown schedule never fires")
    }

    @Test
    fun `a slot that was missed is still due at the next check, once`() {
        val tuesdayNoon = at("2026-11-03T12:00:00", "UTC")
        assertTrue(EmailService.isDue("DAILY", 8, 1, instant("2026-11-02T08:00:05"), tuesdayNoon), "8:00 passed unsent")
        assertTrue(EmailService.isDue("DAILY", 8, 1, null, tuesdayNoon, notBefore = instant("2026-11-03T08:00:00")))
        assertFalse(EmailService.isDue("DAILY", 8, 1, instant("2026-11-03T12:00:03"), tuesdayNoon), "caught up")
        assertTrue(EmailService.isDue("WEEKLY", 8, 1, instant("2026-10-26T08:00:05"), tuesdayNoon), "Monday's was missed")
        assertTrue(EmailService.isDue("MONTHLY", 8, 1, instant("2026-10-01T08:00:05"), tuesdayNoon), "the 1st was missed")
        assertFalse(EmailService.isDue("DAILY", 8, 1, null, tuesdayNoon, notBefore = instant("2026-11-03T09:00:00")),
            "created after the slot: not owed")
    }

    @Test
    fun `the period is written in plain words`() {
        assertEquals("last 24 hours", periodWords("24h"))
        assertEquals("last 7 days", periodWords("7d"))
        assertEquals("last 30 days", periodWords("30d"))
        assertEquals("last year", periodWords("365d"))
    }

    @Test
    fun `port 465 uses TLS from the start and STARTTLS is required when it is on`() {
        fun props(port: Int, startTls: Boolean) = smtpProperties(
            EmailConfig(smtpHost = "mail.example", smtpPort = port, smtpFrom = "a@b.c", smtpStartTls = startTls), 1000
        )
        val implicit = props(465, true)
        assertEquals("true", implicit["mail.smtp.ssl.enable"])
        assertNull(implicit["mail.smtp.starttls.enable"], "no STARTTLS on top of implicit TLS")
        assertNull(implicit["mail.smtp.starttls.required"])
        assertEquals("true", props(465, false)["mail.smtp.ssl.enable"])

        val starttls = props(587, true)
        assertEquals("true", starttls["mail.smtp.starttls.enable"])
        assertEquals("true", starttls["mail.smtp.starttls.required"])
        assertNull(starttls["mail.smtp.ssl.enable"])

        val plain = props(25, false)
        assertNull(plain["mail.smtp.starttls.enable"])
        assertNull(plain["mail.smtp.ssl.enable"])
        assertEquals("mail.example", plain["mail.smtp.host"])
    }

    private fun report() = ProjectReport(
        totalViews = 10, uniqueVisitors = 5,
        topPages = listOf(StatEntry("/<script>alert(1)</script>", 7)),
        browsers = emptyList(), oss = emptyList(), devices = emptyList(),
        referrers = listOf(StatEntry("https://ref.example", 3)),
        countries = listOf(StatEntry("Sweden", 4)),
        customEvents = listOf(StatEntry("signup", 2)),
        lastVisits = emptyList(), activityHeatmap = emptyList(),
        peakTimeAnalysis = PeakTimeAnalysis(emptyList(), emptyList(), 0, 0),
        bounceRate = 50.0
    )

    @Test
    fun `the email escapes everything that came from the database`() {
        val html = EmailService.buildReportHtml(
            "Bob's \"shop\" <b>", "shop.example", "7d", report(),
            headerText = "<img src=x onerror=alert(1)>", footerText = "a & b"
        )
        assertFalse(html.contains("<script>"), html)
        assertFalse(html.contains("<img"), html)
        assertFalse(html.contains("<b>"), html)
        assertTrue(html.contains("Bob&#39;s &quot;shop&quot; &lt;b&gt;"))
        assertTrue(html.contains("/&lt;script&gt;alert(1)&lt;/script&gt;"))
        assertTrue(html.contains("a &amp; b"))
    }

    @Test
    fun `includeSections picks which blocks are rendered`() {
        val all = EmailService.buildReportHtml("P", "p.example", "7d", report(), null, null)
        for (title in listOf("Page Views", "Top Pages", "Top Referrers", "Top Countries", "Top Events")) {
            assertTrue(all.contains(title), title)
        }
        val some = EmailService.buildReportHtml("P", "p.example", "7d", report(), null, null, setOf("overview", "events"))
        assertTrue(some.contains("Page Views"))
        assertTrue(some.contains("Top Events"))
        for (title in listOf("Top Pages", "Top Referrers", "Top Countries")) {
            assertFalse(some.contains(title), title)
        }
    }
}
