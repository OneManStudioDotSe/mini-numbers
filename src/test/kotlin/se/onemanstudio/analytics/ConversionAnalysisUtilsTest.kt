package se.onemanstudio.analytics

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.After
import org.junit.Test
import org.slf4j.LoggerFactory
import se.onemanstudio.utils.calculateGoalConversions
import se.onemanstudio.config.models.*
import se.onemanstudio.config.models.DatabaseConfig
import se.onemanstudio.core.ServiceManager
import se.onemanstudio.db.Events
import se.onemanstudio.db.Funnels
import se.onemanstudio.db.FunnelSteps
import se.onemanstudio.db.Projects
import se.onemanstudio.utils.analyzeFunnel
import se.onemanstudio.utils.analyzeSegment
import se.onemanstudio.api.models.admin.SegmentFilter
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.*

/**
 * Unit tests for ConversionAnalysisUtils
 * Tests goal conversion calculations and funnel analysis logic
 */
class ConversionAnalysisUtilsTest {

    private val logger = LoggerFactory.getLogger("ConversionAnalysisUtilsTest")

    @After
    fun releaseServices() {
        ServiceManager.shutdown(logger)
    }

    private fun ensureTestDb() {
        val config = AppConfig(
            security = SecurityConfig(
                adminUsername = "test",
                adminPassword = "testpassword123",
                serverSalt = "test-salt-" + "x".repeat(50),
                allowedOrigins = emptyList()
            ),
            database = DatabaseConfig(
                type = DatabaseType.SQLITE,
                path = "./test-dbs/conv-test-${System.currentTimeMillis()}-${Thread.currentThread().id}.db"
            ),
            server = ServerConfig(port = 8080, isDevelopment = true),
            geoip = GeoIPConfig(databasePath = "src/main/resources/geo/geolite2-city.mmdb"),
            rateLimit = RateLimitConfig(perIpRequestsPerMinute = 1000, perApiKeyRequestsPerMinute = 10000)
        )
        ServiceManager.reload(config, logger)
    }

    private fun createProject(name: String = "Test Project"): UUID {
        val id = UUID.randomUUID()
        transaction {
            Projects.insert {
                it[Projects.id] = id
                it[Projects.name] = name
                it[Projects.domain] = "test.com"
                it[Projects.apiKey] = UUID.randomUUID().toString().replace("-", "")
            }
        }
        return id
    }

    private fun insertEvent(
        projectId: UUID,
        sessionId: String,
        path: String,
        eventType: String = "pageview",
        eventName: String? = null,
        timestamp: LocalDateTime = LocalDateTime.now()
    ) {
        transaction {
            Events.insert {
                it[Events.projectId] = projectId
                it[Events.visitorHash] = "hash-$sessionId"
                it[Events.sessionId] = sessionId
                it[Events.path] = path
                it[Events.eventType] = eventType
                it[Events.eventName] = eventName
                it[Events.timestamp] = timestamp
            }
        }
    }

    // ==================== Goal Conversion Tests ====================

    @Test
    fun `URL goal counts sessions that visited matching page`() {
        ensureTestDb()
        val projectId = createProject()
        val now = LocalDateTime.now()

        // Session 1: visits the goal URL
        insertEvent(projectId, "s1", "/checkout/success", timestamp = now.minusHours(1))
        // Session 2: visits other pages
        insertEvent(projectId, "s2", "/home", timestamp = now.minusHours(2))
        insertEvent(projectId, "s2", "/about", timestamp = now.minusHours(1))
        // Session 3: also visits the goal URL
        insertEvent(projectId, "s3", "/checkout/success", timestamp = now.minusMinutes(30))

        val (conversions, rate) = transaction {
            calculateGoalConversions("url", "/checkout/success", projectId, now.minusDays(1), now)
        }

        assertEquals(2L, conversions, "Two sessions should have converted")
        // 2 out of 3 sessions = 66.67%
        assertTrue(rate > 66.0 && rate < 67.0, "Rate should be ~66.67%")
    }

    @Test
    fun `event goal counts sessions with matching custom event`() {
        ensureTestDb()
        val projectId = createProject()
        val now = LocalDateTime.now()

        // Session 1: triggers signup event
        insertEvent(projectId, "s1", "/home", timestamp = now.minusHours(2))
        insertEvent(projectId, "s1", "/home", eventType = "custom", eventName = "signup", timestamp = now.minusHours(1))
        // Session 2: no custom event
        insertEvent(projectId, "s2", "/home", timestamp = now.minusHours(1))

        val (conversions, rate) = transaction {
            calculateGoalConversions("event", "signup", projectId, now.minusDays(1), now)
        }

        assertEquals(1L, conversions)
        assertEquals(50.0, rate)
    }

    @Test
    fun `goal conversions returns zero for empty project`() {
        ensureTestDb()
        val projectId = createProject()
        val now = LocalDateTime.now()

        val (conversions, rate) = transaction {
            calculateGoalConversions("url", "/checkout", projectId, now.minusDays(1), now)
        }

        assertEquals(0L, conversions)
        assertEquals(0.0, rate)
    }

    @Test
    fun `goal conversions with no matching sessions returns zero rate`() {
        ensureTestDb()
        val projectId = createProject()
        val now = LocalDateTime.now()

        insertEvent(projectId, "s1", "/home", timestamp = now.minusHours(1))
        insertEvent(projectId, "s2", "/about", timestamp = now.minusHours(1))

        val (conversions, rate) = transaction {
            calculateGoalConversions("url", "/checkout", projectId, now.minusDays(1), now)
        }

        assertEquals(0L, conversions)
        assertEquals(0.0, rate)
    }

    @Test
    fun `goal conversions counts unique sessions not total events`() {
        ensureTestDb()
        val projectId = createProject()
        val now = LocalDateTime.now()

        // Same session visits goal URL multiple times
        insertEvent(projectId, "s1", "/checkout/success", timestamp = now.minusHours(2))
        insertEvent(projectId, "s1", "/checkout/success", timestamp = now.minusHours(1))
        insertEvent(projectId, "s1", "/checkout/success", timestamp = now.minusMinutes(30))

        val (conversions, rate) = transaction {
            calculateGoalConversions("url", "/checkout/success", projectId, now.minusDays(1), now)
        }

        assertEquals(1L, conversions, "Should count 1 unique session, not 3 events")
        assertEquals(100.0, rate)
    }

    @Test
    fun `goal conversions respects time range`() {
        ensureTestDb()
        val projectId = createProject()
        val now = LocalDateTime.now()

        // Event outside time range
        insertEvent(projectId, "s1", "/checkout", timestamp = now.minusDays(10))
        // Event inside time range
        insertEvent(projectId, "s2", "/checkout", timestamp = now.minusHours(1))

        val (conversions, _) = transaction {
            calculateGoalConversions("url", "/checkout", projectId, now.minusDays(1), now)
        }

        assertEquals(1L, conversions, "Should only count events within time range")
    }

    @Test
    fun `unknown goal type returns zero conversions`() {
        ensureTestDb()
        val projectId = createProject()
        val now = LocalDateTime.now()

        insertEvent(projectId, "s1", "/home", timestamp = now.minusHours(1))

        val (conversions, rate) = transaction {
            calculateGoalConversions("unknown_type", "/home", projectId, now.minusDays(1), now)
        }

        assertEquals(0, conversions.toInt())
        assertEquals(0.0, rate)
    }

    // ==================== Funnel Analysis Tests ====================

    @Test
    fun `funnel analysis tracks sessions through sequential steps`() {
        ensureTestDb()
        val projectId = createProject()
        val funnelId = UUID.randomUUID()
        val now = LocalDateTime.now()

        // Create funnel with 3 URL steps
        transaction {
            Funnels.insert {
                it[id] = funnelId
                it[Funnels.projectId] = projectId
                it[name] = "Checkout Funnel"
            }
            FunnelSteps.insert {
                it[id] = UUID.randomUUID()
                it[FunnelSteps.funnelId] = funnelId
                it[stepNumber] = 1
                it[name] = "Product Page"
                it[stepType] = "url"
                it[matchValue] = "/products/item-1"
            }
            FunnelSteps.insert {
                it[id] = UUID.randomUUID()
                it[FunnelSteps.funnelId] = funnelId
                it[stepNumber] = 2
                it[name] = "Cart"
                it[stepType] = "url"
                it[matchValue] = "/cart"
            }
            FunnelSteps.insert {
                it[id] = UUID.randomUUID()
                it[FunnelSteps.funnelId] = funnelId
                it[stepNumber] = 3
                it[name] = "Checkout"
                it[stepType] = "url"
                it[matchValue] = "/checkout"
            }
        }

        // Session 1: completes all 3 steps
        insertEvent(projectId, "s1", "/products/item-1", timestamp = now.minusMinutes(30))
        insertEvent(projectId, "s1", "/cart", timestamp = now.minusMinutes(20))
        insertEvent(projectId, "s1", "/checkout", timestamp = now.minusMinutes(10))

        // Session 2: completes only step 1 and 2
        insertEvent(projectId, "s2", "/products/item-1", timestamp = now.minusMinutes(25))
        insertEvent(projectId, "s2", "/cart", timestamp = now.minusMinutes(15))

        // Session 3: only step 1
        insertEvent(projectId, "s3", "/products/item-1", timestamp = now.minusMinutes(20))

        // Sessions 4 and 5 never enter the funnel
        insertEvent(projectId, "s4", "/", timestamp = now.minusMinutes(20))
        insertEvent(projectId, "s5", "/about", timestamp = now.minusMinutes(20))

        val analysis = analyzeFunnel(funnelId, projectId, now.minusDays(1), now)

        assertEquals(5L, analysis.totalSessions)
        // Each step is a share of the sessions that entered the funnel, not of every session on the site
        assertEquals(listOf(100.0, 66.7, 33.3), analysis.steps.map { Math.round(it.conversionRate * 10) / 10.0 })
        assertEquals(3, analysis.steps.size)
        // Step 1: 3 sessions
        assertEquals(3L, analysis.steps[0].sessions)
        // Step 2: 2 sessions
        assertEquals(2L, analysis.steps[1].sessions)
        // Step 3: 1 session
        assertEquals(1L, analysis.steps[2].sessions)
    }

    @Test
    fun `funnel analysis calculates drop-off rates`() {
        ensureTestDb()
        val projectId = createProject()
        val funnelId = UUID.randomUUID()
        val now = LocalDateTime.now()

        transaction {
            Funnels.insert {
                it[id] = funnelId
                it[Funnels.projectId] = projectId
                it[name] = "Simple Funnel"
            }
            FunnelSteps.insert {
                it[id] = UUID.randomUUID()
                it[FunnelSteps.funnelId] = funnelId
                it[stepNumber] = 1
                it[name] = "Step 1"
                it[stepType] = "url"
                it[matchValue] = "/step-1"
            }
            FunnelSteps.insert {
                it[id] = UUID.randomUUID()
                it[FunnelSteps.funnelId] = funnelId
                it[stepNumber] = 2
                it[name] = "Step 2"
                it[stepType] = "url"
                it[matchValue] = "/step-2"
            }
        }

        // 4 sessions reach step 1, only 1 reaches step 2
        for (i in 1..4) {
            insertEvent(projectId, "s$i", "/step-1", timestamp = now.minusMinutes(30L + i))
        }
        insertEvent(projectId, "s1", "/step-2", timestamp = now.minusMinutes(20))

        val analysis = analyzeFunnel(funnelId, projectId, now.minusDays(1), now)

        assertEquals(4L, analysis.steps[0].sessions)
        assertEquals(1L, analysis.steps[1].sessions)
        // Drop-off from step 1 to step 2: 3 out of 4 = 75%
        assertEquals(75.0, analysis.steps[1].dropOffRate)
    }

    @Test
    fun `funnel with event-type steps works`() {
        ensureTestDb()
        val projectId = createProject()
        val funnelId = UUID.randomUUID()
        val now = LocalDateTime.now()

        transaction {
            Funnels.insert {
                it[id] = funnelId
                it[Funnels.projectId] = projectId
                it[name] = "Event Funnel"
            }
            FunnelSteps.insert {
                it[id] = UUID.randomUUID()
                it[FunnelSteps.funnelId] = funnelId
                it[stepNumber] = 1
                it[name] = "View Product"
                it[stepType] = "url"
                it[matchValue] = "/product"
            }
            FunnelSteps.insert {
                it[id] = UUID.randomUUID()
                it[FunnelSteps.funnelId] = funnelId
                it[stepNumber] = 2
                it[name] = "Add to Cart"
                it[stepType] = "event"
                it[matchValue] = "add_to_cart"
            }
        }

        // Session completes both steps
        insertEvent(projectId, "s1", "/product", timestamp = now.minusMinutes(20))
        insertEvent(projectId, "s1", "/product", eventType = "custom", eventName = "add_to_cart", timestamp = now.minusMinutes(10))

        val analysis = analyzeFunnel(funnelId, projectId, now.minusDays(1), now)

        assertEquals(1L, analysis.steps[0].sessions)
        assertEquals(1L, analysis.steps[1].sessions)
    }

    @Test
    fun `funnel requires steps in chronological order`() {
        ensureTestDb()
        val projectId = createProject()
        val funnelId = UUID.randomUUID()
        val now = LocalDateTime.now()

        transaction {
            Funnels.insert {
                it[id] = funnelId
                it[Funnels.projectId] = projectId
                it[name] = "Order Funnel"
            }
            FunnelSteps.insert {
                it[id] = UUID.randomUUID()
                it[FunnelSteps.funnelId] = funnelId
                it[stepNumber] = 1
                it[name] = "Step A"
                it[stepType] = "url"
                it[matchValue] = "/a"
            }
            FunnelSteps.insert {
                it[id] = UUID.randomUUID()
                it[FunnelSteps.funnelId] = funnelId
                it[stepNumber] = 2
                it[name] = "Step B"
                it[stepType] = "url"
                it[matchValue] = "/b"
            }
        }

        // Session visits /b BEFORE /a - should NOT count for step 2
        insertEvent(projectId, "s1", "/b", timestamp = now.minusMinutes(30))
        insertEvent(projectId, "s1", "/a", timestamp = now.minusMinutes(20))

        val analysis = analyzeFunnel(funnelId, projectId, now.minusDays(1), now)

        assertEquals(1L, analysis.steps[0].sessions)
        // /b happened before /a, so step 2 should NOT count
        assertEquals(0L, analysis.steps[1].sessions)
    }

    @Test
    fun `funnel with empty steps returns empty analysis`() {
        ensureTestDb()
        val projectId = createProject()
        val funnelId = UUID.randomUUID()
        val now = LocalDateTime.now()

        transaction {
            Funnels.insert {
                it[id] = funnelId
                it[Funnels.projectId] = projectId
                it[name] = "Empty Funnel"
            }
        }

        val analysis = analyzeFunnel(funnelId, projectId, now.minusDays(1), now)

        assertEquals(0L, analysis.totalSessions)
        assertTrue(analysis.steps.isEmpty())
    }

    @Test
    fun `funnel with no matching events has zero sessions at each step`() {
        ensureTestDb()
        val projectId = createProject()
        val funnelId = UUID.randomUUID()
        val now = LocalDateTime.now()

        transaction {
            Funnels.insert {
                it[id] = funnelId
                it[Funnels.projectId] = projectId
                it[name] = "No Match Funnel"
            }
            FunnelSteps.insert {
                it[id] = UUID.randomUUID()
                it[FunnelSteps.funnelId] = funnelId
                it[stepNumber] = 1
                it[name] = "Step 1"
                it[stepType] = "url"
                it[matchValue] = "/nonexistent"
            }
        }

        // Events exist but don't match funnel step
        insertEvent(projectId, "s1", "/home", timestamp = now.minusMinutes(10))

        val analysis = analyzeFunnel(funnelId, projectId, now.minusDays(1), now)

        assertEquals(1L, analysis.totalSessions)
        assertEquals(0L, analysis.steps[0].sessions)
    }

    // ==================== Larger seeded dataset ====================

    /** About 5 000 events over 1 200 sessions: a funnel path with out-of-order and repeated steps, plus noise. */
    private fun seedLargeDataset(projectId: UUID, base: LocalDateTime) {
        val browsers = listOf("Chrome", "Firefox", "Safari")
        val countries = listOf("Sweden", "Germany", null, "France", "Sweden")
        transaction {
            for (i in 0 until 1200) {
                val t0 = base.plusSeconds(i * 5L)
                fun add(type: String, path: String, offset: Long, name: String? = null) {
                    Events.insert {
                        it[Events.projectId] = projectId
                        it[Events.visitorHash] = "v${i % 700}"
                        it[Events.sessionId] = "big-$i"
                        it[Events.eventType] = type
                        it[Events.eventName] = name
                        it[Events.path] = path
                        it[Events.timestamp] = t0.plusSeconds(offset)
                        it[Events.browser] = browsers[i % 3]
                        it[Events.device] = if (i % 2 == 0) "Mobile" else "Desktop"
                        it[Events.country] = countries[i % 5]
                    }
                }
                add("pageview", if (i % 6 == 5) "/pricing" else "/", 0)
                if (i % 2 == 0) add("pageview", "/products", 10)
                if (i % 4 == 0) {
                    // Every fourth of these adds to the cart before looking at products, which must not count
                    add("custom", "/products", if (i % 16 == 0) 5 else 30, "add_to_cart")
                    if (i % 32 == 0) add("custom", "/products", 40, "add_to_cart")
                }
                if (i % 8 == 0) add("pageview", "/checkout", 60)
                if (i % 10 == 0) add("heartbeat", "/products", 70)
                if (i % 3 == 0) add("pageview", "/blog/${i % 7}", 80)
            }
        }
    }

    private fun createThreeStepFunnel(projectId: UUID): UUID {
        val funnelId = UUID.randomUUID()
        transaction {
            Funnels.insert {
                it[id] = funnelId
                it[Funnels.projectId] = projectId
                it[name] = "Large funnel"
            }
            listOf(Triple("url", "/products", "Products"), Triple("event", "add_to_cart", "Cart"), Triple("url", "/checkout", "Checkout"))
                .forEachIndexed { index, (type, match, label) ->
                    FunnelSteps.insert {
                        it[id] = UUID.randomUUID()
                        it[FunnelSteps.funnelId] = funnelId
                        it[stepNumber] = index + 1
                        it[name] = label
                        it[stepType] = type
                        it[matchValue] = match
                    }
                }
        }
        return funnelId
    }

    @Test
    fun `funnel analysis on a larger dataset gives the known numbers`() {
        ensureTestDb()
        val projectId = createProject()
        val now = LocalDateTime.now()
        seedLargeDataset(projectId, now.minusHours(3))
        val funnelId = createThreeStepFunnel(projectId)

        val analysis = analyzeFunnel(funnelId, projectId, now.minusDays(1), now)

        val actual = "${analysis.totalSessions} " + analysis.steps.joinToString(" | ") {
            val avg = it.avgTimeFromPrevious?.let { v -> "%.4f".format(v) }
            "${it.sessions} ${"%.4f".format(it.conversionRate)} ${"%.4f".format(it.dropOffRate)} $avg"
        }
        assertEquals("1200 600 100.0000 50.0000 null | 263 43.8333 56.1667 21.4449 | 113 18.8333 57.0342 26.6372", actual)
    }

    @Test
    fun `segment analysis on a larger dataset gives the known numbers`() {
        ensureTestDb()
        val projectId = createProject()
        val now = LocalDateTime.now()
        seedLargeDataset(projectId, now.minusHours(3))
        val segmentId = UUID.randomUUID()
        val cases = listOf(
            // Chrome AND mobile OR path starts with /prod (joined left to right)
            listOf(
                SegmentFilter("browser", "equals", "chrome", "AND"),
                SegmentFilter("device", "equals", "MOBILE", "OR"),
                SegmentFilter("path", "starts_with", "/PROD")
            ),
            listOf(SegmentFilter("country", "not_equals", "sweden")),
            listOf(
                SegmentFilter("path", "contains", "log/", "OR"),
                SegmentFilter("eventType", "equals", "heartbeat", "OR")
            ),
            listOf(SegmentFilter("path", "contains", "%")),
        )
        val actual = cases.joinToString("\n") { filters ->
            val a = analyzeSegment(segmentId, "Large", projectId, filters, now.minusDays(1), now)
            "${a.totalViews} ${a.uniqueVisitors} ${"%.4f".format(a.bounceRate)} ${a.matchingEvents} " +
                a.topPages.joinToString(",") { "${it.label}=${it.value}" }
        }
        assertEquals(
            listOf(
                "1508 350 53.3333 1508 /products=1058,/=200,/checkout=50,/blog/0=29," +
                    "/blog/6=29,/blog/5=29,/blog/4=29,/blog/3=28," +
                    "/blog/2=28,/blog/1=28",
                "1074 280 33.3333 1074 /=400,/products=374,/pricing=80,/checkout=60," +
                    "/blog/3=23,/blog/6=23,/blog/4=23,/blog/0=23," +
                    "/blog/5=23,/blog/1=23",
                "520 430 75.0000 520 /products=120,/blog/0=58,/blog/3=57,/blog/6=57," +
                    "/blog/2=57,/blog/5=57,/blog/1=57,/blog/4=57",
                "0 0 0.0000 0 "
            ).joinToString("\n"),
            actual
        )
    }
}
