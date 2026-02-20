package se.onemanstudio.integration

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.Test
import se.onemanstudio.module
import kotlin.test.*

/**
 * Integration tests for /health endpoint and configuration edge cases
 * Tests health check responses, content types, and field presence
 */
class HealthEndpointTest {

    @Test
    fun `GET health returns 200 with status ok`() = testApplication {
        application { module() }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"status\""), "Response should contain status field")
        assertTrue(body.contains("ok"), "Status should be ok")
    }

    @Test
    fun `GET health returns JSON content type`() = testApplication {
        application { module() }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(
            response.contentType()?.match(ContentType.Application.Json) == true,
            "Content type should be application/json"
        )
    }

    @Test
    fun `GET health includes servicesReady field`() = testApplication {
        application { module() }

        val response = client.get("/health")
        val body = response.bodyAsText()

        assertTrue(body.contains("servicesReady"), "Response should include servicesReady field")
    }

    @Test
    fun `GET health includes setupNeeded field`() = testApplication {
        application { module() }

        val response = client.get("/health")
        val body = response.bodyAsText()

        assertTrue(body.contains("setupNeeded"), "Response should include setupNeeded field")
    }

    @Test
    fun `GET health is accessible without authentication`() = testApplication {
        application { module() }

        val response = client.get("/health")

        // Should NOT redirect to login (302) - health endpoint is public
        assertNotEquals(HttpStatusCode.Found, response.status, "Health endpoint should not redirect to login")
        assertEquals(HttpStatusCode.OK, response.status)
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
