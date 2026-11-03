package se.onemanstudio.services

import org.junit.Test
import se.onemanstudio.api.models.StatEntry
import se.onemanstudio.api.models.dashboard.PeakTimeAnalysis
import se.onemanstudio.api.models.dashboard.ProjectReport
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Scheduling and rendering of email reports (audit item M4). */
class EmailReportRenderingTest {

    private fun at(text: String, zone: String) = ZonedDateTime.of(java.time.LocalDateTime.parse(text), ZoneId.of(zone))

    @Test
    fun `a daily report is due at its hour in its own timezone and only once per day`() {
        val stockholmEight = at("2026-11-03T08:30:00", "Europe/Stockholm") // 07:30 UTC
        assertTrue(EmailService.isDue("DAILY", 8, 1, null, stockholmEight))
        assertFalse(EmailService.isDue("DAILY", 8, 1, LocalDate.parse("2026-11-03"), stockholmEight), "already sent today")
        assertTrue(EmailService.isDue("DAILY", 8, 1, LocalDate.parse("2026-11-02"), stockholmEight), "sent yesterday")
        assertFalse(EmailService.isDue("DAILY", 8, 1, null, stockholmEight.withZoneSameInstant(ZoneId.of("UTC"))),
            "the same instant is 07:xx in UTC, so a UTC report is not due")
    }

    @Test
    fun `weekly and monthly reports also need the right day`() {
        val tuesday3rd = at("2026-11-03T08:05:00", "UTC")
        assertTrue(EmailService.isDue("WEEKLY", 8, 2, null, tuesday3rd), "2 = Tuesday")
        assertFalse(EmailService.isDue("WEEKLY", 8, 1, null, tuesday3rd), "1 = Monday")
        assertTrue(EmailService.isDue("MONTHLY", 8, 3, null, tuesday3rd))
        assertFalse(EmailService.isDue("MONTHLY", 8, 4, null, tuesday3rd))
        assertFalse(EmailService.isDue("YEARLY", 8, 3, null, tuesday3rd), "unknown schedule never fires")
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
