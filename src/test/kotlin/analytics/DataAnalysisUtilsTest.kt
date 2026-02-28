package se.onemanstudio.analytics

import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import org.slf4j.LoggerFactory
import se.onemanstudio.api.models.dashboard.ActivityCell
import se.onemanstudio.config.models.*
import se.onemanstudio.core.ServiceManager
import se.onemanstudio.db.Events
import se.onemanstudio.db.Projects
import se.onemanstudio.utils.analyzePeakTimes
import se.onemanstudio.utils.calculateBounceRate
import se.onemanstudio.utils.generateActivityHeatmap
import se.onemanstudio.utils.generateContributionCalendar
import se.onemanstudio.utils.generateReport
import se.onemanstudio.utils.generateTimeSeries
import se.onemanstudio.utils.getCurrentPeriod
import se.onemanstudio.utils.getPreviousPeriod
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.*

/**
 * Unit tests for DataAnalysisUtils
 * Tests analytics calculation functions against a real SQLite database with known test data
 */
class DataAnalysisUtilsTest {

    private val logger = LoggerFactory.getLogger("DataAnalysisUtilsTest")

    private fun createTestConfig(): AppConfig {
        return AppConfig(
            security = SecurityConfig(
                adminUsername = "test",
                adminPassword = "testpassword123",
                serverSalt = "test-salt-" + "x".repeat(50),
                allowedOrigins = emptyList()
            ),
            database = DatabaseConfig(
                type = DatabaseType.SQLITE,
                path = "./test-${System.currentTimeMillis()}-${Thread.currentThread().id}.db",
                host = null,
                port = null,
                name = null,
                username = null,
                password = null,
            ),
            server = ServerConfig(port = 8080, isDevelopment = true),
            geoip = GeoIPConfig(databasePath = "src/main/resources/geo/geolite2-city.mmdb"),
            rateLimit = RateLimitConfig(perIpRequestsPerMinute = 1000, perApiKeyRequestsPerMinute = 10000)
        )
    }

    private fun initAndSeed(
        eventCount: Int = 0,
        customSetup: ((UUID) -> Unit)? = null
    ): UUID {
        val config = createTestConfig()
        ServiceManager.reload(config, logger)

        val projectId = UUID.randomUUID()
        transaction {
            Projects.insert {
                it[id] = projectId
                it[name] = "Test Project"
                it[domain] = "test.com"
                it[apiKey] = "test-key-${UUID.randomUUID()}"
            }
        }

        if (customSetup != null) {
            transaction { customSetup(projectId) }
        }

        return projectId
    }

    // ==================== Period Calculation Tests ====================

    @Test
    fun `getCurrentPeriod returns correct range for 24h filter`() {
        val (start, end) = getCurrentPeriod("24h")
        val hours = java.time.Duration.between(start, end).toHours()
        assertEquals(24, hours)
    }

    @Test
    fun `getCurrentPeriod returns correct range for 3d filter`() {
        val (start, end) = getCurrentPeriod("3d")
        val days = java.time.Duration.between(start, end).toDays()
        assertEquals(3, days)
    }

    @Test
    fun `getCurrentPeriod returns correct range for 7d filter`() {
        val (start, end) = getCurrentPeriod("7d")
        val days = java.time.Duration.between(start, end).toDays()
        assertEquals(7, days)
    }

    @Test
    fun `getCurrentPeriod returns correct range for 30d filter`() {
        val (start, end) = getCurrentPeriod("30d")
        val days = java.time.Duration.between(start, end).toDays()
        assertEquals(30, days)
    }

    @Test
    fun `getCurrentPeriod returns correct range for 365d filter`() {
        val (start, end) = getCurrentPeriod("365d")
        val days = java.time.Duration.between(start, end).toDays()
        assertEquals(365, days)
    }

    @Test
    fun `getCurrentPeriod defaults to 7d for unknown filter`() {
        val (start, end) = getCurrentPeriod("unknown")
        val days = java.time.Duration.between(start, end).toDays()
        assertEquals(7, days)
    }

    @Test
    fun `getPreviousPeriod returns range of equal duration before current`() {
        val (currentStart, _) = getCurrentPeriod("7d")
        val (previousStart, previousEnd) = getPreviousPeriod("7d")

        // Previous period end should equal current period start
        val diffSeconds = java.time.Duration.between(previousEnd, currentStart).abs().seconds
        assertTrue(diffSeconds < 2, "Previous period end should be close to current period start")

        // Previous period should have same duration (7 days)
        val days = java.time.Duration.between(previousStart, previousEnd).toDays()
        assertEquals(7, days)
    }

    // ==================== Report Generation Tests ====================

    @Test
    fun `generateReport returns correct totalViews count`() {
        val now = LocalDateTime.now()
        val projectId = initAndSeed { pid ->
            repeat(5) { i ->
                Events.insert {
                    it[Events.projectId] = pid
                    it[visitorHash] = "visitor-$i"
                    it[sessionId] = "session-$i"
                    it[eventType] = "pageview"
                    it[path] = "/page-$i"
                    it[timestamp] = now.minusHours(i.toLong())
                    it[duration] = 0
                }
            }
        }

        val report = generateReport(projectId, now.minusDays(1), now.plusMinutes(1))
        assertEquals(5, report.totalViews)
    }

    @Test
    fun `generateReport returns correct uniqueVisitors count`() {
        val now = LocalDateTime.now()
        val projectId = initAndSeed { pid ->
            // 3 events from 2 unique visitors
            Events.insert {
                it[Events.projectId] = pid
                it[visitorHash] = "visitor-a"
                it[sessionId] = "session-1"
                it[eventType] = "pageview"
                it[path] = "/home"
                it[timestamp] = now.minusHours(1)
                it[duration] = 0
            }
            Events.insert {
                it[Events.projectId] = pid
                it[visitorHash] = "visitor-a"
                it[sessionId] = "session-1"
                it[eventType] = "pageview"
                it[path] = "/about"
                it[timestamp] = now.minusMinutes(30)
                it[duration] = 0
            }
            Events.insert {
                it[Events.projectId] = pid
                it[visitorHash] = "visitor-b"
                it[sessionId] = "session-2"
                it[eventType] = "pageview"
                it[path] = "/home"
                it[timestamp] = now.minusMinutes(15)
                it[duration] = 0
            }
        }

        val report = generateReport(projectId, now.minusDays(1), now.plusMinutes(1))
        assertEquals(2, report.uniqueVisitors)
    }

    @Test
    fun `generateReport returns top pages sorted by count`() {
        val now = LocalDateTime.now()
        val projectId = initAndSeed { pid ->
            // /home visited 3 times, /about visited 1 time
            repeat(3) { i ->
                Events.insert {
                    it[Events.projectId] = pid
                    it[visitorHash] = "visitor-$i"
                    it[sessionId] = "session-$i"
                    it[eventType] = "pageview"
                    it[path] = "/home"
                    it[timestamp] = now.minusMinutes(i.toLong() * 10)
                    it[duration] = 0
                }
            }
            Events.insert {
                it[Events.projectId] = pid
                it[visitorHash] = "visitor-x"
                it[sessionId] = "session-x"
                it[eventType] = "pageview"
                it[path] = "/about"
                it[timestamp] = now.minusMinutes(5)
                it[duration] = 0
            }
        }

        val report = generateReport(projectId, now.minusDays(1), now.plusMinutes(1))
        assertTrue(report.topPages.isNotEmpty())
        assertEquals("/home", report.topPages.first().label)
        assertEquals(3, report.topPages.first().value)
    }

    @Test
    fun `generateReport includes custom events breakdown`() {
        val now = LocalDateTime.now()
        val projectId = initAndSeed { pid ->
            Events.insert {
                it[Events.projectId] = pid
                it[visitorHash] = "visitor-1"
                it[sessionId] = "session-1"
                it[eventType] = "custom"
                it[eventName] = "signup"
                it[path] = "/home"
                it[timestamp] = now.minusMinutes(10)
                it[duration] = 0
            }
            Events.insert {
                it[Events.projectId] = pid
                it[visitorHash] = "visitor-2"
                it[sessionId] = "session-2"
                it[eventType] = "custom"
                it[eventName] = "signup"
                it[path] = "/home"
                it[timestamp] = now.minusMinutes(5)
                it[duration] = 0
            }
            Events.insert {
                it[Events.projectId] = pid
                it[visitorHash] = "visitor-3"
                it[sessionId] = "session-3"
                it[eventType] = "custom"
                it[eventName] = "download"
                it[path] = "/docs"
                it[timestamp] = now.minusMinutes(1)
                it[duration] = 0
            }
        }

        val report = generateReport(projectId, now.minusDays(1), now.plusMinutes(1))
        assertTrue(report.customEvents.isNotEmpty(), "Custom events should not be empty")
        assertEquals("signup", report.customEvents.first().label)
        assertEquals(2, report.customEvents.first().value)
    }

    @Test
    fun `generateReport returns empty lists for project with no events`() {
        val now = LocalDateTime.now()
        val projectId = initAndSeed()

        val report = generateReport(projectId, now.minusDays(1), now.plusMinutes(1))
        assertEquals(0, report.totalViews)
        assertEquals(0, report.uniqueVisitors)
        assertTrue(report.topPages.isEmpty())
        assertTrue(report.customEvents.isEmpty())
        assertTrue(report.lastVisits.isEmpty())
    }

    @Test
    fun `generateReport includes bounce rate`() {
        val now = LocalDateTime.now()
        val projectId = initAndSeed { pid ->
            // One single-page session with no heartbeat = bounced
            Events.insert {
                it[Events.projectId] = pid
                it[visitorHash] = "visitor-1"
                it[sessionId] = "session-bounce"
                it[eventType] = "pageview"
                it[path] = "/home"
                it[timestamp] = now.minusMinutes(10)
                it[duration] = 0
            }
        }

        val report = generateReport(projectId, now.minusDays(1), now.plusMinutes(1))
        assertEquals(100.0, report.bounceRate)
    }

    // ==================== Bounce Rate Tests ====================

    @Test
    fun `calculateBounceRate returns 100 for all single-page sessions`() {
        val now = LocalDateTime.now()
        val projectId = initAndSeed { pid ->
            // Two separate sessions, each with single pageview and no heartbeat
            Events.insert {
                it[Events.projectId] = pid
                it[visitorHash] = "visitor-1"
                it[sessionId] = "session-1"
                it[eventType] = "pageview"
                it[path] = "/home"
                it[timestamp] = now.minusMinutes(10)
                it[duration] = 0
            }
            Events.insert {
                it[Events.projectId] = pid
                it[visitorHash] = "visitor-2"
                it[sessionId] = "session-2"
                it[eventType] = "pageview"
                it[path] = "/about"
                it[timestamp] = now.minusMinutes(5)
                it[duration] = 0
            }
        }

        val bounceRate = transaction { calculateBounceRate(projectId, now.minusDays(1), now.plusMinutes(1)) }
        assertEquals(100.0, bounceRate)
    }

    @Test
    fun `calculateBounceRate returns 0 for all engaged sessions`() {
        val now = LocalDateTime.now()
        val projectId = initAndSeed { pid ->
            // Session with multiple pages = engaged (not bounced)
            Events.insert {
                it[Events.projectId] = pid
                it[visitorHash] = "visitor-1"
                it[sessionId] = "session-1"
                it[eventType] = "pageview"
                it[path] = "/home"
                it[timestamp] = now.minusMinutes(10)
                it[duration] = 0
            }
            Events.insert {
                it[Events.projectId] = pid
                it[visitorHash] = "visitor-1"
                it[sessionId] = "session-1"
                it[eventType] = "pageview"
                it[path] = "/about"
                it[timestamp] = now.minusMinutes(9)
                it[duration] = 0
            }
        }

        val bounceRate = transaction { calculateBounceRate(projectId, now.minusDays(1), now.plusMinutes(1)) }
        assertEquals(0.0, bounceRate)
    }

    @Test
    fun `calculateBounceRate returns 0 for empty project`() {
        val now = LocalDateTime.now()
        val projectId = initAndSeed()

        val bounceRate = transaction { calculateBounceRate(projectId, now.minusDays(1), now.plusMinutes(1)) }
        assertEquals(0.0, bounceRate)
    }

    @Test
    fun `calculateBounceRate correctly identifies sessions with heartbeat as non-bounced`() {
        val now = LocalDateTime.now()
        val projectId = initAndSeed { pid ->
            // Single page but WITH heartbeat = not bounced (user stayed > 30s)
            Events.insert {
                it[Events.projectId] = pid
                it[visitorHash] = "visitor-1"
                it[sessionId] = "session-1"
                it[eventType] = "pageview"
                it[path] = "/home"
                it[timestamp] = now.minusMinutes(10)
                it[duration] = 0
            }
            Events.insert {
                it[Events.projectId] = pid
                it[visitorHash] = "visitor-1"
                it[sessionId] = "session-1"
                it[eventType] = "heartbeat"
                it[path] = "/home"
                it[timestamp] = now.minusMinutes(9)
                it[duration] = 30
            }
        }

        val bounceRate = transaction { calculateBounceRate(projectId, now.minusDays(1), now.plusMinutes(1)) }
        assertEquals(0.0, bounceRate, "Session with heartbeat should not be bounced")
    }

    // ==================== Heatmap & Peak Times Tests ====================

    @Test
    fun `generateActivityHeatmap groups events by day and hour`() {
        val now = LocalDateTime.now()
        val projectId = initAndSeed { pid ->
            // Insert events at known times
            Events.insert {
                it[Events.projectId] = pid
                it[visitorHash] = "visitor-1"
                it[sessionId] = "session-1"
                it[eventType] = "pageview"
                it[path] = "/home"
                it[timestamp] = now.minusHours(1)
                it[duration] = 0
            }
            Events.insert {
                it[Events.projectId] = pid
                it[visitorHash] = "visitor-2"
                it[sessionId] = "session-2"
                it[eventType] = "pageview"
                it[path] = "/home"
                it[timestamp] = now.minusHours(1)
                it[duration] = 0
            }
        }

        val heatmap = generateActivityHeatmap(projectId, now.minusDays(1))
        assertTrue(heatmap.isNotEmpty(), "Heatmap should contain data")
        // Both events at the same hour should be grouped together
        val totalCount = heatmap.sumOf { it.count }
        assertEquals(2, totalCount)
    }

    @Test
    fun `analyzePeakTimes returns top 5 hours and top 3 days`() {
        val heatmapData = listOf(
            ActivityCell(dayOfWeek = 0, hourOfDay = 9, count = 10),
            ActivityCell(dayOfWeek = 0, hourOfDay = 10, count = 20),
            ActivityCell(dayOfWeek = 1, hourOfDay = 14, count = 30),
            ActivityCell(dayOfWeek = 1, hourOfDay = 15, count = 5),
            ActivityCell(dayOfWeek = 2, hourOfDay = 11, count = 15),
            ActivityCell(dayOfWeek = 3, hourOfDay = 16, count = 25),
            ActivityCell(dayOfWeek = 4, hourOfDay = 9, count = 8),
        )

        val peakTimes = analyzePeakTimes(heatmapData)
        assertTrue(peakTimes.topHours.size <= 5, "Should have at most 5 top hours")
        assertTrue(peakTimes.topDays.size <= 3, "Should have at most 3 top days")
    }

    @Test
    fun `analyzePeakTimes identifies correct peak hour and day`() {
        val heatmapData = listOf(
            ActivityCell(dayOfWeek = 0, hourOfDay = 9, count = 5),
            ActivityCell(dayOfWeek = 1, hourOfDay = 14, count = 100),
            ActivityCell(dayOfWeek = 2, hourOfDay = 10, count = 10),
        )

        val peakTimes = analyzePeakTimes(heatmapData)
        assertEquals(14, peakTimes.peakHour, "Peak hour should be 14 (highest count)")
        assertEquals(1, peakTimes.peakDay, "Peak day should be 1 (Monday, highest count)")
    }

    @Test
    fun `analyzePeakTimes handles empty heatmap data`() {
        val peakTimes = analyzePeakTimes(emptyList())
        assertTrue(peakTimes.topHours.isEmpty())
        assertTrue(peakTimes.topDays.isEmpty())
        assertEquals(0, peakTimes.peakHour)
        assertEquals(0, peakTimes.peakDay)
    }

    // ==================== Time Series Tests ====================

    @Test
    fun `generateTimeSeries uses hourly granularity for 24h filter`() {
        val now = LocalDateTime.now()
        val projectId = initAndSeed { pid ->
            // Insert events at 2 different hours
            Events.insert {
                it[Events.projectId] = pid
                it[visitorHash] = "visitor-1"
                it[sessionId] = "session-1"
                it[eventType] = "pageview"
                it[path] = "/home"
                it[timestamp] = now.minusHours(2)
                it[duration] = 0
            }
            Events.insert {
                it[Events.projectId] = pid
                it[visitorHash] = "visitor-2"
                it[sessionId] = "session-2"
                it[eventType] = "pageview"
                it[path] = "/home"
                it[timestamp] = now.minusHours(5)
                it[duration] = 0
            }
        }

        val timeSeries = generateTimeSeries(projectId, now.minusDays(1), now.plusMinutes(1), "24h")
        // Should have 2 distinct hourly buckets
        assertEquals(2, timeSeries.size, "Should have 2 hourly time series points")
        assertTrue(timeSeries.all { it.views > 0 })
    }

    @Test
    fun `generateTimeSeries uses daily granularity for 7d filter`() {
        val now = LocalDateTime.now()
        val projectId = initAndSeed { pid ->
            // Insert events on 2 different days
            Events.insert {
                it[Events.projectId] = pid
                it[visitorHash] = "visitor-1"
                it[sessionId] = "session-1"
                it[eventType] = "pageview"
                it[path] = "/home"
                it[timestamp] = now.minusDays(1)
                it[duration] = 0
            }
            Events.insert {
                it[Events.projectId] = pid
                it[visitorHash] = "visitor-2"
                it[sessionId] = "session-2"
                it[eventType] = "pageview"
                it[path] = "/home"
                it[timestamp] = now.minusDays(3)
                it[duration] = 0
            }
        }

        val timeSeries = generateTimeSeries(projectId, now.minusDays(7), now.plusMinutes(1), "7d")
        assertEquals(2, timeSeries.size, "Should have 2 daily time series points")
    }

    @Test
    fun `generateTimeSeries counts unique visitors per bucket`() {
        val now = LocalDateTime.now()
        val projectId = initAndSeed { pid ->
            // 3 events in same hour: 2 from same visitor, 1 from different
            // Truncate to hour start so +5/+10 min never cross the hour boundary
            val sameHour = now.minusHours(1).truncatedTo(java.time.temporal.ChronoUnit.HOURS)
            Events.insert {
                it[Events.projectId] = pid
                it[visitorHash] = "visitor-a"
                it[sessionId] = "session-1"
                it[eventType] = "pageview"
                it[path] = "/home"
                it[timestamp] = sameHour
                it[duration] = 0
            }
            Events.insert {
                it[Events.projectId] = pid
                it[visitorHash] = "visitor-a"
                it[sessionId] = "session-1"
                it[eventType] = "pageview"
                it[path] = "/about"
                it[timestamp] = sameHour.plusMinutes(5)
                it[duration] = 0
            }
            Events.insert {
                it[Events.projectId] = pid
                it[visitorHash] = "visitor-b"
                it[sessionId] = "session-2"
                it[eventType] = "pageview"
                it[path] = "/home"
                it[timestamp] = sameHour.plusMinutes(10)
                it[duration] = 0
            }
        }

        val timeSeries = generateTimeSeries(projectId, now.minusDays(1), now.plusMinutes(1), "24h")
        assertEquals(1, timeSeries.size, "All events in same hour should be grouped")
        assertEquals(3, timeSeries.first().views, "Should count all 3 events as views")
        assertEquals(2, timeSeries.first().uniqueVisitors, "Should count 2 unique visitors")
    }

    // ==================== Contribution Calendar Tests ====================

    @Test
    fun `generateContributionCalendar assigns intensity levels correctly`() {
        val now = LocalDateTime.now()
        val projectId = initAndSeed { pid ->
            // Insert events to create different intensity levels
            // High traffic day: 40 events
            repeat(40) { i ->
                Events.insert {
                    it[Events.projectId] = pid
                    it[visitorHash] = "visitor-$i"
                    it[sessionId] = "session-$i"
                    it[eventType] = "pageview"
                    it[path] = "/home"
                    it[timestamp] = now.minusDays(1).plusMinutes(i.toLong())
                    it[duration] = 0
                }
            }
            // Low traffic day: 5 events
            repeat(5) { i ->
                Events.insert {
                    it[Events.projectId] = pid
                    it[visitorHash] = "visitor-low-$i"
                    it[sessionId] = "session-low-$i"
                    it[eventType] = "pageview"
                    it[path] = "/home"
                    it[timestamp] = now.minusDays(2).plusMinutes(i.toLong())
                    it[duration] = 0
                }
            }
        }

        val calendar = generateContributionCalendar(projectId)
        assertTrue(calendar.days.isNotEmpty())
        assertEquals(40, calendar.maxVisits)

        // High traffic day should have level 4
        val highDay = calendar.days.find { it.visits == 40L }
        assertNotNull(highDay)
        assertEquals(4, highDay.level, "40 events (100% of max) should be level 4")

        // Low traffic day should have level 1 (5/40 = 12.5% < 25%)
        val lowDay = calendar.days.find { it.visits == 5L }
        assertNotNull(lowDay)
        assertEquals(1, lowDay.level, "5 events (12.5% of max) should be level 1")
    }

    @Test
    fun `generateContributionCalendar returns sorted dates`() {
        val now = LocalDateTime.now()
        val projectId = initAndSeed { pid ->
            // Insert events on 3 different days (out of order)
            Events.insert {
                it[Events.projectId] = pid
                it[visitorHash] = "visitor-1"
                it[sessionId] = "session-1"
                it[eventType] = "pageview"
                it[path] = "/home"
                it[timestamp] = now.minusDays(10)
                it[duration] = 0
            }
            Events.insert {
                it[Events.projectId] = pid
                it[visitorHash] = "visitor-2"
                it[sessionId] = "session-2"
                it[eventType] = "pageview"
                it[path] = "/home"
                it[timestamp] = now.minusDays(1)
                it[duration] = 0
            }
            Events.insert {
                it[Events.projectId] = pid
                it[visitorHash] = "visitor-3"
                it[sessionId] = "session-3"
                it[eventType] = "pageview"
                it[path] = "/home"
                it[timestamp] = now.minusDays(5)
                it[duration] = 0
            }
        }

        val calendar = generateContributionCalendar(projectId)
        assertTrue(calendar.days.size >= 3)

        // Verify dates are sorted in ascending order
        for (i in 0 until calendar.days.size - 1) {
            assertTrue(
                calendar.days[i].date <= calendar.days[i + 1].date,
                "Dates should be sorted: ${calendar.days[i].date} should be <= ${calendar.days[i + 1].date}"
            )
        }
    }
}
