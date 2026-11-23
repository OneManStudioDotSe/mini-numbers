package se.onemanstudio.services

import com.sun.net.httpserver.HttpServer
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.slf4j.LoggerFactory
import se.onemanstudio.config.models.*
import se.onemanstudio.core.ServiceManager
import se.onemanstudio.db.ConversionGoals
import se.onemanstudio.db.Projects
import se.onemanstudio.db.WebhookDeliveries
import se.onemanstudio.db.Webhooks
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Webhook delivery against a real HTTP receiver on localhost: what arrives, how it is signed,
 * when it is retried, what the delivery log says, and which events of /collect fire it.
 */
class WebhookDeliveryTest {

    private data class Received(val path: String, val body: String, val headers: Map<String, String>)

    private val logger = LoggerFactory.getLogger("WebhookDeliveryTest")
    private val received = CopyOnWriteArrayList<Received>()
    /** Status codes the receiver answers with, in order; 200 once the queue is empty. */
    private val answers = ConcurrentLinkedQueue<Int>()
    private lateinit var receiver: HttpServer
    private lateinit var project: UUID
    private val signingSecret = "test-secret-0123456789"

    @Before
    fun start() {
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
                    path = "./test-dbs/webhook-test-${System.nanoTime()}.db"
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
                it[name] = "Webhook delivery"
                it[domain] = "test.com"
                it[apiKey] = UUID.randomUUID().toString().replace("-", "")
            }
        }

        receiver = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        receiver.createContext("/") { exchange ->
            received += Received(
                path = exchange.requestURI.path,
                body = exchange.requestBody.readBytes().decodeToString(),
                headers = exchange.requestHeaders.entries.associate { it.key.lowercase() to it.value.first() }
            )
            val reply = "received".toByteArray()
            exchange.sendResponseHeaders(answers.poll() ?: 200, reply.size.toLong())
            exchange.responseBody.use { it.write(reply) }
        }
        receiver.start()
        WebhookService.retryStepMillis = 50
    }

    @After
    fun stop() {
        WebhookService.retryStepMillis = 5_000
        receiver.stop(0)
        ServiceManager.shutdown(logger)
    }

    private fun receiverUrl(path: String) = "http://127.0.0.1:${receiver.address.port}$path"

    private fun webhook(url: String, events: String = "goal_conversion", active: Boolean = true): UUID {
        val webhookId = UUID.randomUUID()
        transaction {
            Webhooks.insert {
                it[id] = webhookId
                it[projectId] = project
                it[Webhooks.url] = url
                it[Webhooks.secret] = signingSecret
                it[Webhooks.events] = events
                it[isActive] = active
            }
        }
        return webhookId
    }

    /** The delivery log of a webhook, oldest attempt first: (attempt, status, response code, delivered). */
    private fun log(webhookId: UUID) = transaction {
        WebhookDeliveries.selectAll().where { WebhookDeliveries.webhookId eq webhookId }
            .orderBy(WebhookDeliveries.attempt, SortOrder.ASC)
            .map {
                listOf(
                    it[WebhookDeliveries.attempt], it[WebhookDeliveries.status],
                    it[WebhookDeliveries.responseCode], it[WebhookDeliveries.deliveredAt] != null
                )
            }
    }

    private fun waitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition()) {
            assertTrue(System.currentTimeMillis() < deadline, "timed out waiting for $what")
            Thread.sleep(20)
        }
    }

    /** Long enough for a retry or a second delivery to have arrived if one was coming. */
    private fun settle() = Thread.sleep(500)

    private fun hmacSha256(body: String, key: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(), "HmacSHA256"))
        return mac.doFinal(body.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `a delivery arrives once, signed with the webhook's secret, and is logged as a success`() {
        val id = webhook(receiverUrl("/hook"))
        val payload = """{"event":"test","message":"héllo ✓"}"""

        WebhookService.deliverAsync(id, receiverUrl("/hook"), signingSecret, "test", payload)
        waitUntil("the delivery log") { log(id).isNotEmpty() }
        settle()

        val request = received.single()
        assertEquals("/hook", request.path)
        assertEquals(payload, request.body)
        assertEquals("sha256=" + hmacSha256(payload, signingSecret), request.headers["x-mininumbers-signature"])
        assertEquals("test", request.headers["x-mininumbers-event"])
        assertEquals("application/json", request.headers["content-type"])
        assertEquals(listOf(listOf(1, "success", 200, true)), log(id))
    }

    @Test
    fun `a refused delivery is retried until it succeeds, with the same body and signature`() {
        val id = webhook(receiverUrl("/flaky"))
        answers += listOf(500, 503)

        WebhookService.deliverAsync(id, receiverUrl("/flaky"), signingSecret, "goal_conversion", """{"n":1}""")
        waitUntil("three attempts") { log(id).size == 3 }
        settle()

        assertEquals(
            listOf(listOf(1, "failed", 500, false), listOf(2, "failed", 503, false), listOf(3, "success", 200, true)),
            log(id)
        )
        assertEquals(3, received.size)
        assertEquals(1, received.map { it.body to it.headers["x-mininumbers-signature"] }.toSet().size)
    }

    @Test
    fun `a receiver that keeps failing is tried three times and then left alone`() {
        val id = webhook(receiverUrl("/down"))
        answers += List(10) { 500 }

        WebhookService.deliverAsync(id, receiverUrl("/down"), signingSecret, "goal_conversion", """{"n":1}""")
        waitUntil("three attempts") { log(id).size == 3 }
        settle()

        assertEquals(3, received.size)
        assertEquals(listOf(1, 2, 3), log(id).map { it[0] })
        assertTrue(log(id).all { it[1] == "failed" && it[3] == false })
    }

    @Test
    fun `a receiver that cannot be reached is logged as failed without a response code`() {
        val url = receiverUrl("/gone")
        receiver.stop(0)
        val id = webhook(url)

        WebhookService.deliverAsync(id, url, signingSecret, "goal_conversion", """{"n":1}""")
        waitUntil("three attempts") { log(id).size == 3 }

        assertTrue(log(id).all { it[1] == "failed" && it[2] == null }, log(id).toString())
    }

    @Test
    fun `a converting event fires the active goal webhooks once per session`() {
        val goalId = UUID.randomUUID()
        transaction {
            ConversionGoals.insert {
                it[id] = goalId
                it[projectId] = project
                it[name] = "Signed up"
                it[goalType] = "event"
                it[matchValue] = "signup"
            }
        }
        webhook(receiverUrl("/active"))
        webhook(receiverUrl("/paused"), active = false)
        webhook(receiverUrl("/spikes-only"), events = "traffic_spike")
        WebhookTrigger.invalidateProject(project.toString())

        WebhookTrigger.checkAndFire(project, "custom", "signup", "/welcome", "session-1")
        waitUntil("the first conversion") { received.size == 1 }
        WebhookTrigger.checkAndFire(project, "custom", "signup", "/welcome", "session-1") // same visit again
        WebhookTrigger.checkAndFire(project, "custom", "newsletter", "/welcome", "session-1") // not a goal
        WebhookTrigger.checkAndFire(project, "pageview", null, "/signup", "session-1") // a page, not the event
        WebhookTrigger.checkAndFire(project, "custom", "signup", "/welcome", "session-2")
        waitUntil("the second visit's conversion") { received.size == 2 }
        settle()

        assertEquals(listOf("/active", "/active"), received.map { it.path })
        val request = received.first()
        assertEquals("goal_conversion", request.headers["x-mininumbers-event"])
        assertEquals("sha256=" + hmacSha256(request.body, signingSecret), request.headers["x-mininumbers-signature"])
        for (expected in listOf(
            """"event":"goal_conversion"""", """"projectId":"$project"""", """"goalId":"$goalId"""",
            """"goalName":"Signed up"""", """"eventName":"signup"""", """"path":"/welcome""""
        )) assertTrue(request.body.contains(expected), "$expected in ${request.body}")
        assertNull(Regex("session|visitor|ip", RegexOption.IGNORE_CASE).find(request.body), "nothing about the visitor")
        assertNotNull(Regex(""""timestamp":"[^"]+"""").find(request.body))
    }

    @Test
    fun `a retry waiting when its webhook is paused is not sent`() {
        WebhookService.retryStepMillis = 400
        val id = webhook(receiverUrl("/paused-midway"))
        answers += listOf(500, 500)

        WebhookService.deliverAsync(id, receiverUrl("/paused-midway"), signingSecret, "goal_conversion", """{"n":1}""")
        waitUntil("the first attempt") { log(id).size == 1 }
        transaction { Webhooks.update({ Webhooks.id eq id }) { it[isActive] = false } }
        Thread.sleep(1_200)

        assertEquals(1, received.size)
        assertEquals(1, log(id).size)
    }

    @Test
    fun `a retry waiting when its webhook is deleted is not sent`() {
        WebhookService.retryStepMillis = 400
        val id = webhook(receiverUrl("/deleted-midway"))
        answers += listOf(500, 500)

        WebhookService.deliverAsync(id, receiverUrl("/deleted-midway"), signingSecret, "goal_conversion", """{"n":1}""")
        waitUntil("the first attempt") { log(id).size == 1 }
        transaction {
            WebhookDeliveries.deleteWhere { WebhookDeliveries.webhookId eq id }
            Webhooks.deleteWhere { Webhooks.id eq id }
        }
        Thread.sleep(1_200)

        assertEquals(1, received.size)
        assertEquals(0, log(id).size)
    }

    @Test
    fun `a link-local address is refused at delivery without a request or a retry`() {
        val url = "http://169.254.169.254/latest/meta-data"
        val id = webhook(url)

        WebhookService.deliverAsync(id, url, signingSecret, "goal_conversion", """{"n":1}""")
        waitUntil("the delivery log") { log(id).isNotEmpty() }
        settle()

        assertEquals(listOf(listOf(1, "failed", null, false)), log(id))
    }

    @Test
    fun `delivery history older than thirty days is trimmed and newer is kept`() {
        val id = webhook(receiverUrl("/trim"))
        fun row(daysAgo: Long) = transaction {
            WebhookDeliveries.insert {
                it[WebhookDeliveries.id] = UUID.randomUUID()
                it[webhookId] = id
                it[eventType] = "test"
                it[payload] = "{}"
                it[createdAt] = java.time.LocalDateTime.now().minusDays(daysAgo)
            }
        }
        row(40); row(31); row(29); row(1)

        assertEquals(2, WebhookService.trimDeliveries())
        assertEquals(2, log(id).size)
    }
}
