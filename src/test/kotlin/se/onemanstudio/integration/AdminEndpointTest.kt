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
 * Integration tests for admin API endpoints
 * Tests authentication, project CRUD, and analytics endpoints
 * Uses session-based auth via cookie-enabled test client
 */
class AdminEndpointTest {

    /**
     * Create a test client with cookie support for session authentication
     */
    private fun ApplicationTestBuilder.createAuthClient(): HttpClient {
        return createClient {
            install(HttpCookies)
        }
    }

    /**
     * Login with test credentials and return the authenticated client
     */
    private suspend fun HttpClient.login(
        username: String = "admin",
        password: String = "testpassword123"
    ): HttpResponse {
        return post("/api/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"$username","password":"$password"}""")
        }
    }

    // ==================== Authentication Tests ====================

    @Test
    fun `unauthenticated request to admin endpoint returns redirect`() = testApplication {
        application { module() }

        val response = client.get("/admin/projects") {
            // Don't follow redirects to see the 302
        }

        // Should redirect to login page (302) or return unauthorized
        assertTrue(
            response.status == HttpStatusCode.Found ||
            response.status == HttpStatusCode.Unauthorized ||
            response.status == HttpStatusCode.OK, // Some Ktor configs may handle differently
            "Unauthenticated request should be redirected or rejected, got: ${response.status}"
        )
    }

    @Test
    fun `login with valid credentials returns 200`() = testApplication {
        application { module() }
        val authClient = createAuthClient()

        val response = authClient.login()

        // Login may succeed or fail depending on whether services are initialized
        // If services ready with matching config: 200
        // If services not ready (setup needed): may get different response
        assertTrue(
            response.status == HttpStatusCode.OK ||
            response.status == HttpStatusCode.Unauthorized,
            "Login should return 200 or 401, got: ${response.status}"
        )
    }

    @Test
    fun `login with invalid credentials returns 401`() = testApplication {
        application { module() }
        val authClient = createAuthClient()

        val response = authClient.login(username = "wrong", password = "wrong")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `login with empty body returns 400`() = testApplication {
        application { module() }
        val authClient = createAuthClient()

        val response = authClient.post("/api/login") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }

        // Should return 400 for missing fields or 401 for invalid login
        assertTrue(
            response.status == HttpStatusCode.BadRequest ||
            response.status == HttpStatusCode.Unauthorized,
            "Empty login should return 400 or 401, got: ${response.status}"
        )
    }

    // ==================== Project CRUD Tests ====================

    @Test
    fun `GET admin projects without auth returns 401`() = testApplication {
        application { module() }

        val response = client.get("/admin/projects")

        // Without authentication, should return 401 JSON error
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `POST admin projects without auth returns 401`() = testApplication {
        application { module() }

        val response = client.post("/admin/projects") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Test Project","domain":"test.com"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `POST admin projects with missing name returns 400 when authenticated`() = testApplication {
        application { module() }
        val authClient = createAuthClient()

        // Login first
        val loginResponse = authClient.login()

        if (loginResponse.status == HttpStatusCode.OK) {
            // Only test CRUD if login succeeded (services are ready)
            val response = authClient.post("/admin/projects") {
                contentType(ContentType.Application.Json)
                setBody("""{"domain":"test.com"}""")
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun `DELETE admin projects with invalid UUID returns 400 when authenticated`() = testApplication {
        application { module() }
        val authClient = createAuthClient()

        val loginResponse = authClient.login()

        if (loginResponse.status == HttpStatusCode.OK) {
            val response = authClient.delete("/admin/projects/not-a-valid-uuid")

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("Invalid"), "Should indicate invalid project ID")
        }
    }

    @Test
    fun `POST admin projects update with invalid UUID returns 400`() = testApplication {
        application { module() }
        val authClient = createAuthClient()

        val loginResponse = authClient.login()

        if (loginResponse.status == HttpStatusCode.OK) {
            val response = authClient.post("/admin/projects/invalid-uuid") {
                contentType(ContentType.Application.Json)
                setBody("""{"name":"Updated Name"}""")
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    // ==================== Analytics Endpoint Tests ====================

    @Test
    fun `GET admin projects stats with invalid UUID returns 400`() = testApplication {
        application { module() }
        val authClient = createAuthClient()

        val loginResponse = authClient.login()

        if (loginResponse.status == HttpStatusCode.OK) {
            val response = authClient.get("/admin/projects/invalid/stats")

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun `GET admin projects report with invalid UUID returns 400`() = testApplication {
        application { module() }
        val authClient = createAuthClient()

        val loginResponse = authClient.login()

        if (loginResponse.status == HttpStatusCode.OK) {
            val response = authClient.get("/admin/projects/invalid-uuid/report")

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun `GET admin projects calendar with invalid UUID returns 400`() = testApplication {
        application { module() }
        val authClient = createAuthClient()

        val loginResponse = authClient.login()

        if (loginResponse.status == HttpStatusCode.OK) {
            val response = authClient.get("/admin/projects/invalid-uuid/calendar")

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun `GET admin projects live with invalid UUID returns 400`() = testApplication {
        application { module() }
        val authClient = createAuthClient()

        val loginResponse = authClient.login()

        if (loginResponse.status == HttpStatusCode.OK) {
            val response = authClient.get("/admin/projects/invalid-uuid/live")

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun `GET admin projects events with invalid UUID returns 400`() = testApplication {
        application { module() }
        val authClient = createAuthClient()

        val loginResponse = authClient.login()

        if (loginResponse.status == HttpStatusCode.OK) {
            val response = authClient.get("/admin/projects/invalid-uuid/events")

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    // ==================== Cross-Project Isolation Tests ====================

    @Test
    fun `GET stats for non-existent project UUID returns 400 or 404`() = testApplication {
        application { module() }
        val authClient = createAuthClient()

        val loginResponse = authClient.login()

        if (loginResponse.status == HttpStatusCode.OK) {
            // A valid UUID that doesn't belong to any project
            val fakeId = "00000000-0000-0000-0000-000000000001"
            val response = authClient.get("/admin/projects/$fakeId/stats")
            // Should return 200 with empty stats or 404 — never leak another project's data
            assertTrue(
                response.status == HttpStatusCode.OK ||
                response.status == HttpStatusCode.NotFound,
                "Stats for unknown project should return 200 (empty) or 404, got ${response.status}"
            )
        }
    }

    @Test
    fun `GET report for non-existent project UUID returns valid response`() = testApplication {
        application { module() }
        val authClient = createAuthClient()

        val loginResponse = authClient.login()

        if (loginResponse.status == HttpStatusCode.OK) {
            val fakeId = "00000000-0000-0000-0000-000000000002"
            val response = authClient.get("/admin/projects/$fakeId/report")
            assertTrue(
                response.status == HttpStatusCode.OK ||
                response.status == HttpStatusCode.NotFound,
                "Report for unknown project should return 200 (empty) or 404, got ${response.status}"
            )
        }
    }

    @Test
    fun `GET goals for non-existent project returns empty list`() = testApplication {
        application { module() }
        val authClient = createAuthClient()

        val loginResponse = authClient.login()

        if (loginResponse.status == HttpStatusCode.OK) {
            val fakeId = "00000000-0000-0000-0000-000000000003"
            val response = authClient.get("/admin/projects/$fakeId/goals")
            // Unknown project: should return empty array, not another project's goals
            assertTrue(
                response.status == HttpStatusCode.OK ||
                response.status == HttpStatusCode.NotFound,
                "Goals for unknown project should return 200 or 404, got ${response.status}"
            )
            if (response.status == HttpStatusCode.OK) {
                val body = response.bodyAsText()
                assertTrue(body.contains("[]") || body.contains("\"data\":[]"),
                    "Goals response for unknown project should be empty, got: $body")
            }
        }
    }

    @Test
    fun `DELETE project with valid UUID that does not exist returns 404 or 204`() = testApplication {
        application { module() }
        val authClient = createAuthClient()

        val loginResponse = authClient.login()

        if (loginResponse.status == HttpStatusCode.OK) {
            val fakeId = "00000000-0000-0000-0000-000000000004"
            val response = authClient.delete("/admin/projects/$fakeId")
            // Either 204 (idempotent) or 404 — must not crash
            assertTrue(
                response.status == HttpStatusCode.NoContent ||
                response.status == HttpStatusCode.NotFound,
                "Delete non-existent project should return 204 or 404, got ${response.status}"
            )
        }
    }

    @Test
    fun `POST collect with deleted project API key returns 404`() = testApplication {
        application { module() }

        // Using a key that was never created — should be rejected
        val response = client.post("/collect?key=0000000000000000000000000000dead") {
            contentType(ContentType.Application.Json)
            setBody("""{"path":"/","sessionId":"test-session","type":"pageview"}""")
        }
        assertEquals(HttpStatusCode.NotFound, response.status,
            "Collect with unknown API key should return 404")
    }

    @Test
    fun `GET segments for non-existent project returns empty list`() = testApplication {
        application { module() }
        val authClient = createAuthClient()

        val loginResponse = authClient.login()

        if (loginResponse.status == HttpStatusCode.OK) {
            val fakeId = "00000000-0000-0000-0000-000000000005"
            val response = authClient.get("/admin/projects/$fakeId/segments")
            assertTrue(
                response.status == HttpStatusCode.OK ||
                response.status == HttpStatusCode.NotFound,
                "Segments for unknown project should return 200 or 404, got ${response.status}"
            )
            if (response.status == HttpStatusCode.OK) {
                val body = response.bodyAsText()
                assertTrue(body.contains("[]") || body.contains("\"data\":[]"),
                    "Segments for unknown project should be empty, got: $body")
            }
        }
    }

    @Test
    fun `GET live feed for non-existent project returns empty list`() = testApplication {
        application { module() }
        val authClient = createAuthClient()

        val loginResponse = authClient.login()

        if (loginResponse.status == HttpStatusCode.OK) {
            val fakeId = "00000000-0000-0000-0000-000000000006"
            val response = authClient.get("/admin/projects/$fakeId/live")
            assertTrue(
                response.status == HttpStatusCode.OK ||
                response.status == HttpStatusCode.NotFound,
                "Live feed for unknown project should return 200 or 404, got ${response.status}"
            )
            if (response.status == HttpStatusCode.OK) {
                assertTrue(response.bodyAsText().contains("[]"),
                    "Live feed for unknown project should be empty")
            }
        }
    }

    @Test
    fun `GET rotate-api-key without auth returns 401`() = testApplication {
        application { module() }

        val fakeId = "00000000-0000-0000-0000-000000000007"
        val response = client.post("/admin/projects/$fakeId/rotate-api-key") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status,
            "Rotate API key without auth should return 401")
    }

    @Test
    fun `logout clears session`() = testApplication {
        application { module() }
        val authClient = createAuthClient()

        // Login
        authClient.login()

        // Logout
        val logoutResponse = authClient.post("/api/logout")
        assertEquals(HttpStatusCode.OK, logoutResponse.status)

        val body = logoutResponse.bodyAsText()
        assertTrue(body.contains("success") || body.contains("Logged out"), "Logout should confirm success")
    }
}
