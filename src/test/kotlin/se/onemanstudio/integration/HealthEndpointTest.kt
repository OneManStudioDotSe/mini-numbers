package se.onemanstudio.integration

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.Test
import se.onemanstudio.module
import kotlin.test.*

/**
 * Integration tests for /health endpoint
 *
 * The health endpoint may be served by either:
 * - SetupRouting: returns "ok"/"error" with servicesReady/setupNeeded fields
 * - Routing: returns "healthy"/"unhealthy" with state/version fields
 *
 * Which is active depends on whether .env exists and ServiceManager state.
 */
class HealthEndpointTest {

    @Test
    fun `GET health returns valid response`() = testApplication {
        application { module() }

        val response = client.get("/health")

        assertTrue(
            response.status == HttpStatusCode.OK || response.status == HttpStatusCode.ServiceUnavailable,
            "Health endpoint should return 200 or 503, got ${response.status}"
        )
        val body = response.bodyAsText()
        assertTrue(body.contains("\"status\""), "Response should contain status field")
        // Status may be "ok", "error", "healthy", or "unhealthy" depending on which endpoint is active
        assertTrue(
            body.contains("ok") || body.contains("error") ||
            body.contains("healthy") || body.contains("unhealthy"),
            "Status should indicate health state"
        )
    }

    @Test
    fun `GET health returns JSON content type`() = testApplication {
        application { module() }

        val response = client.get("/health")

        assertTrue(
            response.status == HttpStatusCode.OK || response.status == HttpStatusCode.ServiceUnavailable,
            "Health endpoint should return a valid status"
        )
        assertTrue(
            response.contentType()?.match(ContentType.Application.Json) == true,
            "Content type should be application/json"
        )
    }

    @Test
    fun `GET health includes status field`() = testApplication {
        application { module() }

        val response = client.get("/health")
        val body = response.bodyAsText()

        // Both health endpoint variants include a "status" field
        assertTrue(body.contains("\"status\""), "Response should include status field")
    }

    @Test
    fun `GET health includes relevant state information`() = testApplication {
        application { module() }

        val response = client.get("/health")
        val body = response.bodyAsText()

        // Routing.kt health includes "state" and "version"
        // SetupRouting.kt health includes "servicesReady" and "setupNeeded"
        assertTrue(
            body.contains("servicesReady") || body.contains("state") || body.contains("version"),
            "Response should include state information"
        )
    }

    @Test
    fun `GET health is accessible without authentication`() = testApplication {
        application { module() }

        val response = client.get("/health")

        // Should NOT redirect to login (302) - health endpoint is public
        assertNotEquals(HttpStatusCode.Found, response.status, "Health endpoint should not redirect to login")
        assertTrue(
            response.status == HttpStatusCode.OK || response.status == HttpStatusCode.ServiceUnavailable,
            "Should return 200 or 503"
        )
    }

    @Test
    fun `GET health response is valid JSON`() = testApplication {
        application { module() }

        val response = client.get("/health")
        val body = response.bodyAsText()

        // Verify it's valid JSON by checking structure
        assertTrue(body.startsWith("{"), "Response should be a JSON object")
        assertTrue(body.endsWith("}"), "Response should be a JSON object")
        assertTrue(body.contains("\"status\""), "Should contain status key")
    }
}
