package se.onemanstudio.analytics

import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.slf4j.LoggerFactory
import se.onemanstudio.config.models.*
import se.onemanstudio.core.ServiceManager
import se.onemanstudio.db.Events
import se.onemanstudio.db.Projects
import se.onemanstudio.utils.RevenueAnalysisUtils
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals

/**
 * The numbers of the revenue lab, checked against events with known amounts.
 * (RevenueAnalysisUtilsTest covers the endpoints' status codes and shapes.)
 */
class RevenueNumbersTest {

    private val logger = LoggerFactory.getLogger("RevenueNumbersTest")
    private val now: LocalDateTime = LocalDateTime.now()
    private val weekStart: LocalDateTime = now.minusDays(7)
    private lateinit var project: UUID

    @Before
    fun freshDatabase() {
        ServiceManager.reload(
            AppConfig(
                security = SecurityConfig(
                    adminUsername = "test",
                    adminPassword = "testpassword123",
                    serverSalt = "test-salt-" + "x".repeat(50),
                    allowedOrigins = emptyList()
                ),
                database = DatabaseConfig(
                    type = DatabaseType.SQLITE,
                    path = "./test-dbs/revenue-test-${System.nanoTime()}.db"
                ),
                server = ServerConfig(port = 8080, isDevelopment = true),
                geoip = GeoIPConfig(databasePath = "src/main/resources/geo/geolite2-city.mmdb"),
                rateLimit = RateLimitConfig(perIpRequestsPerMinute = 1000, perApiKeyRequestsPerMinute = 10000)
            ),
            logger
        )
        project = UUID.randomUUID()
        transaction {
            Projects.insert {
                it[id] = project
                it[name] = "Revenue numbers"
                it[domain] = "test.com"
                it[apiKey] = UUID.randomUUID().toString().replace("-", "")
            }
        }
    }

    @After
    fun releaseServices() {
        ServiceManager.shutdown(logger)
    }

    private fun pageview(
        session: String,
        referrer: String? = null,
        utmSource: String? = null,
        utmCampaign: String? = null,
        at: LocalDateTime = now.minusHours(2)
    ) = transaction {
        Events.insert {
            it[projectId] = project
            it[visitorHash] = "visitor-$session"
            it[sessionId] = session
            it[path] = "/"
            it[eventType] = "pageview"
            it[Events.referrer] = referrer
            it[Events.utmSource] = utmSource
            it[Events.utmCampaign] = utmCampaign
            it[timestamp] = at
        }
    }

    private fun event(session: String, name: String, properties: String?, at: LocalDateTime = now.minusHours(1)) =
        transaction {
            Events.insert {
                it[projectId] = project
                it[visitorHash] = "visitor-$session"
                it[sessionId] = session
                it[path] = "/checkout"
                it[eventType] = "custom"
                it[eventName] = name
                it[Events.properties] = properties
                it[timestamp] = at
            }
        }

    @Test
    fun `totals, average order, revenue per visitor and the previous period`() {
        pageview("a")
        pageview("b")
        event("a", "purchase", """{"revenue":30,"currency":"EUR"}""")
        event("a", "purchase", """{"revenue":"10"}""")
        event("b", "subscription", """{"revenue": 100.5}""")
        event("b", "signup", """{"plan":"pro"}""")
        event("b", "ping", null)
        event("old", "purchase", """{"revenue":50}""", at = now.minusDays(10))
        event("older", "purchase", """{"revenue":999}""", at = now.minusDays(20))

        val stats = RevenueAnalysisUtils.calculateRevenue(project, weekStart, now)

        assertEquals(140.5, stats.totalRevenue)
        assertEquals(3, stats.transactions)
        assertEquals(46.83, stats.averageOrderValue)
        assertEquals(70.25, stats.revenuePerVisitor)
        assertEquals(50.0, stats.previousRevenue)
        assertEquals(1, stats.previousTransactions)
    }

    @Test
    fun `only a top-level numeric revenue counts`() {
        event("a", "purchase", """{"revenue":1e3}""")
        event("a", "purchase", """{"revenue":0.5}""")
        event("a", "refund", """{"revenue":-20}""")
        event("a", "purchase", """{"revenue":"12abc"}""")
        event("a", "purchase", """{"revenue":null}""")
        event("a", "purchase", """{"total_revenue":7}""")
        event("a", "purchase", """{"cart":{"revenue":7}}""")
        event("a", "purchase", """{"note":"\"revenue\":7"}""")
        event("a", "purchase", "not json")

        val stats = RevenueAnalysisUtils.calculateRevenue(project, weekStart, now)

        assertEquals(1000.5, stats.totalRevenue)
        assertEquals(2, stats.transactions)
    }

    @Test
    fun `a total above 21 million is not capped`() {
        event("a", "purchase", """{"revenue":30000000}""")
        event("a", "purchase", """{"revenue":0.25}""")

        assertEquals(30_000_000.25, RevenueAnalysisUtils.calculateRevenue(project, weekStart, now).totalRevenue)
        assertEquals(30_000_000.25, RevenueAnalysisUtils.calculateRevenueByEvent(project, weekStart, now).single().revenue)
    }

    @Test
    fun `revenue per event name, highest first`() {
        event("a", "purchase", """{"revenue":30}""")
        event("b", "purchase", """{"revenue":10}""")
        event("b", "subscription", """{"revenue":100.5}""")
        event("b", "signup", """{"plan":"pro"}""")

        val byEvent = RevenueAnalysisUtils.calculateRevenueByEvent(project, weekStart, now)

        assertEquals(listOf("subscription", "purchase"), byEvent.map { it.eventName })
        assertEquals(listOf(100.5, 40.0), byEvent.map { it.revenue })
        assertEquals(listOf(1L, 2L), byEvent.map { it.transactions })
        assertEquals(listOf(100.5, 20.0), byEvent.map { it.avgValue })
    }

    @Test
    fun `revenue is attributed to the source of the session's first page view`() {
        pageview("google-buyer", referrer = "https://www.google.com/search")
        pageview("google-buyer", referrer = "https://tracked.test/pricing", at = now.minusMinutes(90))
        pageview("google-visitor", referrer = "https://google.com/")
        pageview("newsletter", utmSource = "newsletter")
        pageview("campaign", referrer = "https://news.example.com/", utmSource = "x", utmCampaign = "spring")
        pageview("direct")
        event("google-buyer", "purchase", """{"revenue":30}""")
        event("newsletter", "purchase", """{"revenue":10}""")
        event("newsletter", "purchase", """{"revenue":5}""")
        event("campaign", "purchase", """{"revenue":2}""")
        event("direct", "purchase", """{"revenue":1}""")

        val bySource = RevenueAnalysisUtils.calculateRevenueAttribution(project, weekStart, now).associateBy { it.source }

        assertEquals(listOf("google.com", "utm:newsletter", "utm:spring", "Direct"), bySource.keys.toList())
        assertEquals(listOf(30.0, 15.0, 2.0, 1.0), bySource.values.map { it.revenue })
        // Two purchases in one visit are two transactions, as in the totals
        assertEquals(listOf(1L, 2L, 1L, 1L), bySource.values.map { it.transactions })
        assertEquals(7.5, bySource.getValue("utm:newsletter").avgValue)
        // One of the two visits from Google bought something
        assertEquals(50.0, bySource.getValue("google.com").conversionRate)
        assertEquals(100.0, bySource.getValue("utm:newsletter").conversionRate)
    }
}
