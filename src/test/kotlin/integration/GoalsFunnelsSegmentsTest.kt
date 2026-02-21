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
 * Integration tests for Goals, Funnels, and Segments endpoints
 * Tests CRUD operations and analytics for advanced features
 */
class GoalsFunnelsSegmentsTest {

    private fun ApplicationTestBuilder.createAuthClient(): HttpClient {
        return createClient { install(HttpCookies) }
    }

    private suspend fun HttpClient.login(): HttpResponse {
        return post("/api/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"testpassword123"}""")
        }
    }

    private fun extractProjectId(json: String): String? {
        return """"id"\s*:\s*"([^"]+)"""".toRegex().find(json)?.groupValues?.get(1)
    }

    private fun extractFieldValue(json: String, field: String): String? {
        return """"$field"\s*:\s*"([^"]+)"""".toRegex().find(json)?.groupValues?.get(1)
    }

    private suspend fun ApplicationTestBuilder.setupProject(authClient: HttpClient): String? {
        authClient.post("/admin/projects") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Goals Test Project","domain":"goals-test.com"}""")
        }
        val projectsBody = authClient.get("/admin/projects").bodyAsText()
        return extractProjectId(projectsBody)
    }

    // ==================== Goals Tests ====================

    @Test
    fun `create and list goals`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        if (authClient.login().status != HttpStatusCode.OK) return@testApplication

        val projectId = setupProject(authClient) ?: return@testApplication

        // Create a URL goal
        val createResponse = authClient.post("/admin/projects/$projectId/goals") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Checkout Goal","goalType":"url","matchValue":"/checkout/success"}""")
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)

        // List goals
        val listResponse = authClient.get("/admin/projects/$projectId/goals")
        assertEquals(HttpStatusCode.OK, listResponse.status)
        val body = listResponse.bodyAsText()
        assertTrue(body.contains("Checkout Goal"))
        assertTrue(body.contains("/checkout/success"))
    }

    @Test
    fun `create event-type goal`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        if (authClient.login().status != HttpStatusCode.OK) return@testApplication

        val projectId = setupProject(authClient) ?: return@testApplication

        val createResponse = authClient.post("/admin/projects/$projectId/goals") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Signup Goal","goalType":"event","matchValue":"signup"}""")
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)
        assertTrue(createResponse.bodyAsText().contains("success"))
    }

    @Test
    fun `create goal with blank name fails`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        if (authClient.login().status != HttpStatusCode.OK) return@testApplication

        val projectId = setupProject(authClient) ?: return@testApplication

        val response = authClient.post("/admin/projects/$projectId/goals") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"","goalType":"url","matchValue":"/checkout"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `create goal with invalid type fails`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        if (authClient.login().status != HttpStatusCode.OK) return@testApplication

        val projectId = setupProject(authClient) ?: return@testApplication

        val response = authClient.post("/admin/projects/$projectId/goals") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Bad Goal","goalType":"invalid","matchValue":"/test"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `delete goal returns 204`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        if (authClient.login().status != HttpStatusCode.OK) return@testApplication

        val projectId = setupProject(authClient) ?: return@testApplication

        // Create a goal
        val createBody = authClient.post("/admin/projects/$projectId/goals") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Delete Me","goalType":"url","matchValue":"/test"}""")
        }.bodyAsText()
        val goalId = extractFieldValue(createBody, "id") ?: return@testApplication

        // Delete it
        val deleteResponse = authClient.delete("/admin/projects/$projectId/goals/$goalId")
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)
    }

    @Test
    fun `goal stats endpoint returns data`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        if (authClient.login().status != HttpStatusCode.OK) return@testApplication

        val projectId = setupProject(authClient) ?: return@testApplication

        val response = authClient.get("/admin/projects/$projectId/goals/stats?filter=7d")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    // ==================== Funnels Tests ====================

    @Test
    fun `create and list funnels`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        if (authClient.login().status != HttpStatusCode.OK) return@testApplication

        val projectId = setupProject(authClient) ?: return@testApplication

        // Create funnel with steps
        val createResponse = authClient.post("/admin/projects/$projectId/funnels") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "name": "Checkout Funnel",
                    "steps": [
                        {"name": "Product Page", "stepType": "url", "matchValue": "/product"},
                        {"name": "Cart", "stepType": "url", "matchValue": "/cart"},
                        {"name": "Checkout", "stepType": "url", "matchValue": "/checkout"}
                    ]
                }
            """.trimIndent())
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)

        // List funnels
        val listResponse = authClient.get("/admin/projects/$projectId/funnels")
        assertEquals(HttpStatusCode.OK, listResponse.status)
        val body = listResponse.bodyAsText()
        assertTrue(body.contains("Checkout Funnel"))
    }

    @Test
    fun `create funnel with less than 2 steps fails`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        if (authClient.login().status != HttpStatusCode.OK) return@testApplication

        val projectId = setupProject(authClient) ?: return@testApplication

        val response = authClient.post("/admin/projects/$projectId/funnels") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "name": "Too Short Funnel",
                    "steps": [
                        {"name": "Only Step", "stepType": "url", "matchValue": "/only"}
                    ]
                }
            """.trimIndent())
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `create funnel with blank name fails`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        if (authClient.login().status != HttpStatusCode.OK) return@testApplication

        val projectId = setupProject(authClient) ?: return@testApplication

        val response = authClient.post("/admin/projects/$projectId/funnels") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "name": "",
                    "steps": [
                        {"name": "Step 1", "stepType": "url", "matchValue": "/a"},
                        {"name": "Step 2", "stepType": "url", "matchValue": "/b"}
                    ]
                }
            """.trimIndent())
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `create funnel with invalid step type fails`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        if (authClient.login().status != HttpStatusCode.OK) return@testApplication

        val projectId = setupProject(authClient) ?: return@testApplication

        val response = authClient.post("/admin/projects/$projectId/funnels") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "name": "Bad Funnel",
                    "steps": [
                        {"name": "Step 1", "stepType": "invalid", "matchValue": "/a"},
                        {"name": "Step 2", "stepType": "url", "matchValue": "/b"}
                    ]
                }
            """.trimIndent())
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `delete funnel returns 204`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        if (authClient.login().status != HttpStatusCode.OK) return@testApplication

        val projectId = setupProject(authClient) ?: return@testApplication

        val createBody = authClient.post("/admin/projects/$projectId/funnels") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "name": "Delete Me Funnel",
                    "steps": [
                        {"name": "Step 1", "stepType": "url", "matchValue": "/a"},
                        {"name": "Step 2", "stepType": "url", "matchValue": "/b"}
                    ]
                }
            """.trimIndent())
        }.bodyAsText()
        val funnelId = extractFieldValue(createBody, "id") ?: return@testApplication

        val deleteResponse = authClient.delete("/admin/projects/$projectId/funnels/$funnelId")
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)
    }

    // ==================== Segments Tests ====================

    @Test
    fun `create and list segments`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        if (authClient.login().status != HttpStatusCode.OK) return@testApplication

        val projectId = setupProject(authClient) ?: return@testApplication

        val createResponse = authClient.post("/admin/projects/$projectId/segments") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "name": "Chrome Users",
                    "description": "Visitors using Chrome browser",
                    "filters": [
                        {"field": "browser", "operator": "equals", "value": "Chrome", "logic": "AND"}
                    ]
                }
            """.trimIndent())
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)

        val listResponse = authClient.get("/admin/projects/$projectId/segments")
        assertEquals(HttpStatusCode.OK, listResponse.status)
        assertTrue(listResponse.bodyAsText().contains("Chrome Users"))
    }

    @Test
    fun `create segment with blank name fails`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        if (authClient.login().status != HttpStatusCode.OK) return@testApplication

        val projectId = setupProject(authClient) ?: return@testApplication

        val response = authClient.post("/admin/projects/$projectId/segments") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "name": "",
                    "filters": [{"field": "browser", "operator": "equals", "value": "Chrome", "logic": "AND"}]
                }
            """.trimIndent())
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `create segment with no filters fails`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        if (authClient.login().status != HttpStatusCode.OK) return@testApplication

        val projectId = setupProject(authClient) ?: return@testApplication

        val response = authClient.post("/admin/projects/$projectId/segments") {
            contentType(ContentType.Application.Json)
            setBody("""{"name": "Empty Segment", "filters": []}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `create segment with invalid filter field fails`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        if (authClient.login().status != HttpStatusCode.OK) return@testApplication

        val projectId = setupProject(authClient) ?: return@testApplication

        val response = authClient.post("/admin/projects/$projectId/segments") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "name": "Bad Field Segment",
                    "filters": [{"field": "invalid_field", "operator": "equals", "value": "test", "logic": "AND"}]
                }
            """.trimIndent())
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `create segment with invalid operator fails`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        if (authClient.login().status != HttpStatusCode.OK) return@testApplication

        val projectId = setupProject(authClient) ?: return@testApplication

        val response = authClient.post("/admin/projects/$projectId/segments") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "name": "Bad Op Segment",
                    "filters": [{"field": "browser", "operator": "invalid_op", "value": "Chrome", "logic": "AND"}]
                }
            """.trimIndent())
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `delete segment returns 204`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        if (authClient.login().status != HttpStatusCode.OK) return@testApplication

        val projectId = setupProject(authClient) ?: return@testApplication

        val createBody = authClient.post("/admin/projects/$projectId/segments") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "name": "Delete Me",
                    "filters": [{"field": "browser", "operator": "equals", "value": "Chrome", "logic": "AND"}]
                }
            """.trimIndent())
        }.bodyAsText()
        val segmentId = extractFieldValue(createBody, "id") ?: return@testApplication

        val deleteResponse = authClient.delete("/admin/projects/$projectId/segments/$segmentId")
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)
    }

    @Test
    fun `segment analysis returns data`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        if (authClient.login().status != HttpStatusCode.OK) return@testApplication

        val projectId = setupProject(authClient) ?: return@testApplication

        // Create a segment
        val createBody = authClient.post("/admin/projects/$projectId/segments") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "name": "Analysis Segment",
                    "filters": [{"field": "browser", "operator": "equals", "value": "Chrome", "logic": "AND"}]
                }
            """.trimIndent())
        }.bodyAsText()
        val segmentId = extractFieldValue(createBody, "id") ?: return@testApplication

        val analysisResponse = authClient.get("/admin/projects/$projectId/segments/$segmentId/analysis?filter=7d")
        assertEquals(HttpStatusCode.OK, analysisResponse.status)
        val body = analysisResponse.bodyAsText()
        assertTrue(body.contains("segmentId"))
        assertTrue(body.contains("totalViews"))
    }

    // ==================== Invalid Project ID Tests ====================

    @Test
    fun `goals endpoint with invalid UUID returns 400`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        if (authClient.login().status != HttpStatusCode.OK) return@testApplication

        val response = authClient.get("/admin/projects/not-a-uuid/goals")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `funnels endpoint with invalid UUID returns 400`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        if (authClient.login().status != HttpStatusCode.OK) return@testApplication

        val response = authClient.get("/admin/projects/not-a-uuid/funnels")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `segments endpoint with invalid UUID returns 400`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        if (authClient.login().status != HttpStatusCode.OK) return@testApplication

        val response = authClient.get("/admin/projects/not-a-uuid/segments")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
