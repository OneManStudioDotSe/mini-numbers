package se.onemanstudio.analytics

import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import org.slf4j.LoggerFactory
import se.onemanstudio.utils.calculateGoalConversions
import se.onemanstudio.config.models.*
import se.onemanstudio.core.ServiceManager
import se.onemanstudio.db.Events
import se.onemanstudio.db.Funnels
import se.onemanstudio.db.FunnelSteps
import se.onemanstudio.db.Projects
import se.onemanstudio.utils.analyzeFunnel
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.*

/**
 * Unit tests for ConversionAnalysisUtils
 * Tests goal conversion calculations and funnel analysis logic
 */
class ConversionAnalysisUtilsTest {

    private val logger = LoggerFactory.getLogger("ConversionAnalysisUtilsTest")

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

        val analysis = analyzeFunnel(funnelId, projectId, now.minusDays(1), now)

        assertEquals(3L, analysis.totalSessions)
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
}
