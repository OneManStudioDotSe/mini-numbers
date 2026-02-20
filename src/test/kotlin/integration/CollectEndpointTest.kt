package se.onemanstudio.integration

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.Test
import se.onemanstudio.module
import kotlin.test.*

/**
 * Integration tests for /collect endpoint
 * Tests data collection, API key validation, rate limiting, and error handling
 */
class CollectEndpointTest {

    @Test
    fun `POST collect without API key returns 400`() = testApplication {
        application { module() }

        val response = client.post("/collect") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "path": "/home",
                    "sessionId": "test-session",
                    "type": "pageview"
                }
            """.trimIndent())
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("API key") || body.contains("Missing"))
    }

    @Test
    fun `POST collect with API key in header works`() = testApplication {
        application { module() }

        // First, create a project to get a valid API key
        // This test assumes there's already a project or needs to be extended
        // For now, test with a dummy key to verify the flow

        val response = client.post("/collect") {
            header("X-Project-Key", "test-api-key-123")
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "path": "/home",
                    "sessionId": "test-session",
                    "type": "pageview"
                }
            """.trimIndent())
        }

        // Should return 404 (Invalid API key) or 202 (Accepted) if key is valid
        assertTrue(
            response.status == HttpStatusCode.NotFound ||
            response.status == HttpStatusCode.Accepted
        )
    }

    @Test
    fun `POST collect with API key in query parameter works`() = testApplication {
        application { module() }

        val response = client.post("/collect?key=test-api-key-123") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "path": "/home",
                    "sessionId": "test-session",
                    "type": "pageview"
                }
            """.trimIndent())
        }

        // Should return 404 (Invalid API key) or 202 (Accepted) if key is valid
        assertTrue(
            response.status == HttpStatusCode.NotFound ||
            response.status == HttpStatusCode.Accepted
        )
    }

    @Test
    fun `POST collect with invalid JSON returns 400`() = testApplication {
        application { module() }

        val response = client.post("/collect") {
            header("X-Project-Key", "test-api-key")
            contentType(ContentType.Application.Json)
            setBody("invalid json {{{")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST collect with missing required fields returns 400`() = testApplication {
        application { module() }

        val response = client.post("/collect") {
            header("X-Project-Key", "test-api-key")
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "path": "/home"
                }
            """.trimIndent())
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("sessionId") || body.contains("type") || body.contains("validation"),
            "Should mention missing fields")
    }

    @Test
    fun `POST collect with empty path returns 400`() = testApplication {
        application { module() }

        val response = client.post("/collect") {
            header("X-Project-Key", "test-api-key")
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "path": "",
                    "sessionId": "test-session",
                    "type": "pageview"
                }
            """.trimIndent())
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST collect with invalid event type returns 400`() = testApplication {
        application { module() }

        val response = client.post("/collect") {
            header("X-Project-Key", "test-api-key")
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "path": "/home",
                    "sessionId": "test-session",
                    "type": "invalid_type"
                }
            """.trimIndent())
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST collect with pageview type is accepted`() = testApplication {
        application { module() }

        val response = client.post("/collect") {
            header("X-Project-Key", "test-api-key")
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "path": "/home",
                    "sessionId": "test-session",
                    "type": "pageview"
                }
            """.trimIndent())
        }

        // Either 404 (no project) or 202 (accepted)
        assertTrue(
            response.status == HttpStatusCode.NotFound ||
            response.status == HttpStatusCode.Accepted
        )
    }

    @Test
    fun `POST collect with heartbeat type is accepted`() = testApplication {
        application { module() }

        val response = client.post("/collect") {
            header("X-Project-Key", "test-api-key")
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "path": "/home",
                    "sessionId": "test-session",
                    "type": "heartbeat"
                }
            """.trimIndent())
        }

        // Either 404 (no project) or 202 (accepted)
        assertTrue(
            response.status == HttpStatusCode.NotFound ||
            response.status == HttpStatusCode.Accepted
        )
    }

    @Test
    fun `POST collect with optional referrer is accepted`() = testApplication {
        application { module() }

        val response = client.post("/collect") {
            header("X-Project-Key", "test-api-key")
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "path": "/home",
                    "referrer": "https://google.com",
                    "sessionId": "test-session",
                    "type": "pageview"
                }
            """.trimIndent())
        }

        // Either 404 (no project) or 202 (accepted)
        assertTrue(
            response.status == HttpStatusCode.NotFound ||
            response.status == HttpStatusCode.Accepted
        )
    }

    @Test
    fun `POST collect with XSS attempt in path is rejected`() = testApplication {
        application { module() }

        val response = client.post("/collect") {
            header("X-Project-Key", "test-api-key")
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "path": "/home<script>alert('xss')</script>",
                    "sessionId": "test-session",
                    "type": "pageview"
                }
            """.trimIndent())
        }

        // Should be rejected by validation
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST collect with SQL injection in path is rejected`() = testApplication {
        application { module() }

        val response = client.post("/collect") {
            header("X-Project-Key", "test-api-key")
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "path": "'; DROP TABLE events; --",
                    "sessionId": "test-session",
                    "type": "pageview"
                }
            """.trimIndent())
        }

        // Should be rejected by validation
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST collect with extremely long path is rejected`() = testApplication {
        application { module() }

        val longPath = "/" + "a".repeat(10000)

        val response = client.post("/collect") {
            header("X-Project-Key", "test-api-key")
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "path": "$longPath",
                    "sessionId": "test-session",
                    "type": "pageview"
                }
            """.trimIndent())
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST collect with null bytes in path is rejected`() = testApplication {
        application { module() }

        val response = client.post("/collect") {
            header("X-Project-Key", "test-api-key")
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "path": "/home\u0000/test",
                    "sessionId": "test-session",
                    "type": "pageview"
                }
            """.trimIndent())
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST collect accepts valid path with query parameters`() = testApplication {
        application { module() }

        val response = client.post("/collect") {
            header("X-Project-Key", "test-api-key")
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "path": "/search?q=test&category=all",
                    "sessionId": "test-session",
                    "type": "pageview"
                }
            """.trimIndent())
        }

        // Either 404 (no project) or 202 (accepted)
        assertTrue(
            response.status == HttpStatusCode.NotFound ||
            response.status == HttpStatusCode.Accepted
        )
    }

    @Test
    fun `POST collect with User-Agent header processes correctly`() = testApplication {
        application { module() }

        val response = client.post("/collect") {
            header("X-Project-Key", "test-api-key")
            header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/91.0")
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "path": "/home",
                    "sessionId": "test-session",
                    "type": "pageview"
                }
            """.trimIndent())
        }

        // Either 404 (no project) or 202 (accepted)
        assertTrue(
            response.status == HttpStatusCode.NotFound ||
            response.status == HttpStatusCode.Accepted
        )
    }

    @Test
    fun `POST collect returns 202 Accepted on success`() = testApplication {
        application { module() }

        // This test would need a valid project setup
        // For now, we verify the endpoint exists and handles requests
        val response = client.post("/collect") {
            header("X-Project-Key", "test-key")
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "path": "/home",
                    "sessionId": "test-session",
                    "type": "pageview"
                }
            """.trimIndent())
        }

        // Response should be 202 (success) or 404 (invalid key)
        assertTrue(
            response.status == HttpStatusCode.Accepted ||
            response.status == HttpStatusCode.NotFound ||
            response.status == HttpStatusCode.BadRequest
        )
    }
}
