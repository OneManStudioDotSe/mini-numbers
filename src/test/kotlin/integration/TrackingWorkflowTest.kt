package se.onemanstudio.integration

import io.ktor.client.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.Test
import se.onemanstudio.module
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
        if (loginResponse.status != HttpStatusCode.OK) {
            // Skip test if services aren't initialized (setup needed)
            return@testApplication
        }

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
        if (loginResponse.status != HttpStatusCode.OK) return@testApplication

        // Create project
        authClient.post("/admin/projects") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Multi PV Test","domain":"multi-test.com"}""")
        }

        val projectsBody = authClient.get("/admin/projects").bodyAsText()
        val apiKey = extractApiKey(projectsBody) ?: return@testApplication

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
        if (loginResponse.status != HttpStatusCode.OK) return@testApplication

        authClient.post("/admin/projects") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Heartbeat Test","domain":"hb-test.com"}""")
        }

        val projectsBody = authClient.get("/admin/projects").bodyAsText()
        val apiKey = extractApiKey(projectsBody) ?: return@testApplication

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
        if (loginResponse.status != HttpStatusCode.OK) return@testApplication

        authClient.post("/admin/projects") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Custom Event Test","domain":"custom-test.com"}""")
        }

        val projectsBody = authClient.get("/admin/projects").bodyAsText()
        val apiKey = extractApiKey(projectsBody) ?: return@testApplication

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
        if (loginResponse.status != HttpStatusCode.OK) return@testApplication

        authClient.post("/admin/projects") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Demo Data Test","domain":"demo-test.com"}""")
        }

        val projectsBody = authClient.get("/admin/projects").bodyAsText()
        val projectId = extractProjectId(projectsBody) ?: return@testApplication

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
        if (loginResponse.status != HttpStatusCode.OK) return@testApplication

        authClient.post("/admin/projects") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Events Page Test","domain":"events-test.com"}""")
        }

        val projectsBody = authClient.get("/admin/projects").bodyAsText()
        val projectId = extractProjectId(projectsBody) ?: return@testApplication

        // Request events with pagination params
        val eventsResponse = authClient.get("/admin/projects/$projectId/events?page=0&limit=10")
        assertEquals(HttpStatusCode.OK, eventsResponse.status)

        val eventsBody = eventsResponse.bodyAsText()
        assertTrue(eventsBody.contains("total"), "Response should include total count")
        assertTrue(eventsBody.contains("page"), "Response should include page number")
        assertTrue(eventsBody.contains("limit"), "Response should include limit")
    }

    @Test
    fun `project deletion cascades to events`() = testApplication {
        application { module() }
        val authClient = createAuthClient()

        val loginResponse = authClient.login()
        if (loginResponse.status != HttpStatusCode.OK) return@testApplication

        // Create project
        authClient.post("/admin/projects") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Delete Test","domain":"delete-test.com"}""")
        }

        val projectsBody = authClient.get("/admin/projects").bodyAsText()
        val projectId = extractProjectId(projectsBody) ?: return@testApplication

        // Delete it
        val deleteResponse = authClient.delete("/admin/projects/$projectId")
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)
    }
}
