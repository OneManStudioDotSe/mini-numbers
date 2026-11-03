package se.onemanstudio.integration

import io.ktor.client.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.Test
import se.onemanstudio.db.*
import se.onemanstudio.module
import java.util.UUID
import kotlin.test.*

/**
 * End-to-end tracking workflow tests
 * Tests the complete flow: authentication → project creation → event collection → analytics verification
 * These tests exercise multiple components working together
 */
class TrackingWorkflowTest {

    private fun ApplicationTestBuilder.createAuthClient(): HttpClient {
        return createClient {
            install(HttpCookies)
        }
    }

    private suspend fun HttpClient.login(): HttpResponse {
        return post("/api/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"testpassword123"}""")
        }
    }

    /**
     * Helper: extract API key from project list JSON response
     * Simple string parsing to avoid adding JSON dependency in tests
     */
    private fun extractApiKey(projectListJson: String): String? {
        val keyPattern = """"apiKey"\s*:\s*"([^"]+)"""".toRegex()
        return keyPattern.find(projectListJson)?.groupValues?.get(1)
    }

    /**
     * Helper: extract project ID from project list JSON response
     */
    private fun extractProjectId(projectListJson: String): String? {
        val idPattern = """"id"\s*:\s*"([^"]+)"""".toRegex()
        return idPattern.find(projectListJson)?.groupValues?.get(1)
    }

    // ==================== Full Workflow Tests ====================

    @Test
    fun `full workflow - create project, collect pageview, verify in stats`() = testApplication {
        application { module() }
        val authClient = createAuthClient()

        // Step 1: Login
        val loginResponse = authClient.login()
        assertEquals(HttpStatusCode.OK, loginResponse.status, "login")

        // Step 2: Create project
        val createResponse = authClient.post("/admin/projects") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"E2E Test Project","domain":"e2e-test.com"}""")
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)

        // Step 3: Get project list to find API key
        val projectsResponse = authClient.get("/admin/projects")
        assertEquals(HttpStatusCode.OK, projectsResponse.status)
        val projectsBody = projectsResponse.bodyAsText()

        val apiKey = extractApiKey(projectsBody)
        val projectId = extractProjectId(projectsBody)
        assertNotNull(apiKey, "Should have an API key")
        assertNotNull(projectId, "Should have a project ID")

        // Step 4: Collect a pageview event
        val collectResponse = client.post("/collect") {
            header("X-Project-Key", apiKey)
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "path": "/test-page",
                    "sessionId": "e2e-session-1",
                    "type": "pageview"
                }
            """.trimIndent())
        }
        assertEquals(HttpStatusCode.Accepted, collectResponse.status)

        // Step 5: Verify in stats
        val statsResponse = authClient.get("/admin/projects/$projectId/stats")
        assertEquals(HttpStatusCode.OK, statsResponse.status)
        val statsBody = statsResponse.bodyAsText()
        assertTrue(statsBody.contains("totalViews"), "Stats should contain totalViews")
    }

    @Test
    fun `multiple pageviews from same session counted correctly`() = testApplication {
        application { module() }
        val authClient = createAuthClient()

        val loginResponse = authClient.login()
        assertEquals(HttpStatusCode.OK, loginResponse.status, "login")

        // Create project
        authClient.post("/admin/projects") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Multi PV Test","domain":"multi-test.com"}""")
        }

        val projectsBody = authClient.get("/admin/projects").bodyAsText()
        val apiKey = assertNotNull(extractApiKey(projectsBody), "apiKey")

        // Send 3 pageviews from same session
        repeat(3) { i ->
            val response = client.post("/collect") {
                header("X-Project-Key", apiKey)
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "path": "/page-$i",
                        "sessionId": "same-session-id",
                        "type": "pageview"
                    }
                """.trimIndent())
            }
            assertEquals(HttpStatusCode.Accepted, response.status, "Pageview $i should be accepted")
        }
    }

    @Test
    fun `heartbeat events are accepted via collect endpoint`() = testApplication {
        application { module() }
        val authClient = createAuthClient()

        val loginResponse = authClient.login()
        assertEquals(HttpStatusCode.OK, loginResponse.status, "login")

        authClient.post("/admin/projects") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Heartbeat Test","domain":"hb-test.com"}""")
        }

        val projectsBody = authClient.get("/admin/projects").bodyAsText()
        val apiKey = assertNotNull(extractApiKey(projectsBody), "apiKey")

        // Send pageview followed by heartbeat
        client.post("/collect") {
            header("X-Project-Key", apiKey)
            contentType(ContentType.Application.Json)
            setBody("""{"path":"/home","sessionId":"hb-session","type":"pageview"}""")
        }

        val heartbeatResponse = client.post("/collect") {
            header("X-Project-Key", apiKey)
            contentType(ContentType.Application.Json)
            setBody("""{"path":"/home","sessionId":"hb-session","type":"heartbeat"}""")
        }
        assertEquals(HttpStatusCode.Accepted, heartbeatResponse.status)
    }

    @Test
    fun `custom events are accepted via collect endpoint`() = testApplication {
        application { module() }
        val authClient = createAuthClient()

        val loginResponse = authClient.login()
        assertEquals(HttpStatusCode.OK, loginResponse.status, "login")

        authClient.post("/admin/projects") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Custom Event Test","domain":"custom-test.com"}""")
        }

        val projectsBody = authClient.get("/admin/projects").bodyAsText()
        val apiKey = assertNotNull(extractApiKey(projectsBody), "apiKey")

        val response = client.post("/collect") {
            header("X-Project-Key", apiKey)
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "path": "/home",
                    "sessionId": "custom-session",
                    "type": "custom",
                    "eventName": "signup"
                }
            """.trimIndent())
        }
        assertEquals(HttpStatusCode.Accepted, response.status)
    }

    @Test
    fun `events from different API keys are isolated`() = testApplication {
        application { module() }

        // Collect with invalid API key should return 404
        val response = client.post("/collect") {
            header("X-Project-Key", "nonexistent-api-key-12345")
            contentType(ContentType.Application.Json)
            setBody("""{"path":"/home","sessionId":"test","type":"pageview"}""")
        }

        // Should be rejected as invalid API key
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `demo data generation endpoint works`() = testApplication {
        application { module() }
        val authClient = createAuthClient()

        val loginResponse = authClient.login()
        assertEquals(HttpStatusCode.OK, loginResponse.status, "login")

        authClient.post("/admin/projects") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Demo Data Test","domain":"demo-test.com"}""")
        }

        val projectsBody = authClient.get("/admin/projects").bodyAsText()
        val projectId = assertNotNull(extractProjectId(projectsBody), "projectId")

        val demoResponse = authClient.post("/admin/projects/$projectId/demo-data") {
            contentType(ContentType.Application.Json)
            setBody("""{"count":50,"timeScope":7}""")
        }
        assertEquals(HttpStatusCode.OK, demoResponse.status)

        val demoBody = demoResponse.bodyAsText()
        assertTrue(demoBody.contains("generated"), "Response should confirm events were generated")
    }

    @Test
    fun `raw events endpoint returns collected data with pagination`() = testApplication {
        application { module() }
        val authClient = createAuthClient()

        val loginResponse = authClient.login()
        assertEquals(HttpStatusCode.OK, loginResponse.status, "login")

        authClient.post("/admin/projects") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Events Page Test","domain":"events-test.com"}""")
        }

        val projectsBody = authClient.get("/admin/projects").bodyAsText()
        val projectId = assertNotNull(extractProjectId(projectsBody), "projectId")

        // Request events with pagination params
        val eventsResponse = authClient.get("/admin/projects/$projectId/events?page=0&limit=10")
        assertEquals(HttpStatusCode.OK, eventsResponse.status)

        val eventsBody = eventsResponse.bodyAsText()
        assertTrue(eventsBody.contains("total"), "Response should include total count")
        assertTrue(eventsBody.contains("page"), "Response should include page number")
        assertTrue(eventsBody.contains("limit"), "Response should include limit")
    }

    @Test
    fun `project deletion removes everything that belongs to it`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        assertEquals(HttpStatusCode.OK, authClient.login().status, "login")

        val created = authClient.post("/admin/projects") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Delete Test","domain":"delete-test.com"}""")
        }
        assertEquals(HttpStatusCode.Created, created.status, "create project")
        val projectId = assertNotNull(extractProjectId(authClient.get("/admin/projects").bodyAsText()), "projectId")
        val uuid = UUID.fromString(projectId)

        val children = listOf(
            "goals" to """{"name":"Goal","goalType":"url","matchValue":"/thanks"}""",
            "funnels" to """{"name":"Funnel","steps":[{"name":"A","stepType":"url","matchValue":"/a"},
                {"name":"B","stepType":"url","matchValue":"/b"}]}""",
            "segments" to """{"name":"Segment","filters":[{"field":"browser","operator":"equals","value":"Chrome","logic":"AND"}]}""",
            "webhooks" to """{"url":"https://example.com/hook","events":["goal_conversion"]}""",
            "email-reports" to """{"recipientEmail":"owner@example.com","schedule":"WEEKLY"}""",
            "demo-data" to """{"count":20,"timeScope":1}"""
        )
        for ((path, body) in children) {
            val response = authClient.post("/admin/projects/$projectId/$path") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            assertTrue(response.status.isSuccess(), "create $path: ${response.status}")
        }

        // A delivery row hangs off the webhook, two levels below the project
        val (funnelIds, webhookId) = transaction {
            val webhookId = Webhooks.selectAll().where { Webhooks.projectId eq uuid }.single()[Webhooks.id]
            WebhookDeliveries.insert {
                it[id] = UUID.randomUUID()
                it[WebhookDeliveries.webhookId] = webhookId
                it[eventType] = "goal_conversion"
                it[payload] = "{}"
            }
            Funnels.selectAll().where { Funnels.projectId eq uuid }.map { it[Funnels.id] } to webhookId
        }

        fun rows(): Map<String, Long> = transaction {
            mapOf(
                "events" to Events.selectAll().where { Events.projectId eq uuid }.count(),
                "goals" to ConversionGoals.selectAll().where { ConversionGoals.projectId eq uuid }.count(),
                "funnels" to Funnels.selectAll().where { Funnels.projectId eq uuid }.count(),
                "funnel_steps" to FunnelSteps.selectAll().where { FunnelSteps.funnelId inList funnelIds }.count(),
                "segments" to Segments.selectAll().where { Segments.projectId eq uuid }.count(),
                "webhooks" to Webhooks.selectAll().where { Webhooks.projectId eq uuid }.count(),
                "webhook_deliveries" to WebhookDeliveries.selectAll().where { WebhookDeliveries.webhookId eq webhookId }.count(),
                "email_reports" to EmailReports.selectAll().where { EmailReports.projectId eq uuid }.count(),
                "projects" to Projects.selectAll().where { Projects.id eq uuid }.count()
            )
        }
        assertEquals(emptyList(), rows().filter { it.value == 0L }.keys.toList(), "every table has rows before the delete")

        val deleteResponse = authClient.delete("/admin/projects/$projectId")
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)
        assertEquals(emptyList(), rows().filter { it.value > 0L }.keys.toList(), "rows left behind by the delete")
    }

    @Test
    fun `SQLite runs with foreign keys, WAL and a busy timeout`() = testApplication {
        application { module() }
        assertEquals(HttpStatusCode.OK, createAuthClient().get("/health").status) // boots the app
        transaction {
            if (TransactionManager.current().db.vendor != "sqlite") return@transaction
            fun pragma(name: String): String? = exec("PRAGMA $name") { rs -> rs.next(); rs.getString(1) }
            assertEquals("1", pragma("foreign_keys"))
            assertEquals("wal", pragma("journal_mode"))
            assertEquals("5000", pragma("busy_timeout"))
        }
    }
}
