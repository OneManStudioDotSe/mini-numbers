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
        assertEquals(HttpStatusCode.OK, authClient.login().status, "login")

        val projectId = assertNotNull(setupProject(authClient), "projectId")

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
        assertEquals(HttpStatusCode.OK, authClient.login().status, "login")

        val projectId = assertNotNull(setupProject(authClient), "projectId")

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
        assertEquals(HttpStatusCode.OK, authClient.login().status, "login")

        val projectId = assertNotNull(setupProject(authClient), "projectId")

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
        assertEquals(HttpStatusCode.OK, authClient.login().status, "login")

        val projectId = assertNotNull(setupProject(authClient), "projectId")

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
        assertEquals(HttpStatusCode.OK, authClient.login().status, "login")

        val projectId = assertNotNull(setupProject(authClient), "projectId")

        // Create a goal
        val createBody = authClient.post("/admin/projects/$projectId/goals") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Delete Me","goalType":"url","matchValue":"/test"}""")
        }.bodyAsText()
        val goalId = assertNotNull(extractFieldValue(createBody, "id"), "goalId")

        // Delete it
        val deleteResponse = authClient.delete("/admin/projects/$projectId/goals/$goalId")
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)
    }

    @Test
    fun `goal stats endpoint returns data`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        assertEquals(HttpStatusCode.OK, authClient.login().status, "login")

        val projectId = assertNotNull(setupProject(authClient), "projectId")

        val response = authClient.get("/admin/projects/$projectId/goals/stats?filter=7d")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    // ==================== Funnels Tests ====================

    @Test
    fun `create and list funnels`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        assertEquals(HttpStatusCode.OK, authClient.login().status, "login")

        val projectId = assertNotNull(setupProject(authClient), "projectId")

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
        assertEquals(HttpStatusCode.OK, authClient.login().status, "login")

        val projectId = assertNotNull(setupProject(authClient), "projectId")

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
        assertEquals(HttpStatusCode.OK, authClient.login().status, "login")

        val projectId = assertNotNull(setupProject(authClient), "projectId")

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
        assertEquals(HttpStatusCode.OK, authClient.login().status, "login")

        val projectId = assertNotNull(setupProject(authClient), "projectId")

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
        assertEquals(HttpStatusCode.OK, authClient.login().status, "login")

        val projectId = assertNotNull(setupProject(authClient), "projectId")

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
        val funnelId = assertNotNull(extractFieldValue(createBody, "id"), "funnelId")

        val deleteResponse = authClient.delete("/admin/projects/$projectId/funnels/$funnelId")
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)
    }

    // ==================== Segments Tests ====================

    @Test
    fun `create and list segments`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        assertEquals(HttpStatusCode.OK, authClient.login().status, "login")

        val projectId = assertNotNull(setupProject(authClient), "projectId")

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
        assertEquals(HttpStatusCode.OK, authClient.login().status, "login")

        val projectId = assertNotNull(setupProject(authClient), "projectId")

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
        assertEquals(HttpStatusCode.OK, authClient.login().status, "login")

        val projectId = assertNotNull(setupProject(authClient), "projectId")

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
        assertEquals(HttpStatusCode.OK, authClient.login().status, "login")

        val projectId = assertNotNull(setupProject(authClient), "projectId")

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
        assertEquals(HttpStatusCode.OK, authClient.login().status, "login")

        val projectId = assertNotNull(setupProject(authClient), "projectId")

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
        assertEquals(HttpStatusCode.OK, authClient.login().status, "login")

        val projectId = assertNotNull(setupProject(authClient), "projectId")

        val createBody = authClient.post("/admin/projects/$projectId/segments") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "name": "Delete Me",
                    "filters": [{"field": "browser", "operator": "equals", "value": "Chrome", "logic": "AND"}]
                }
            """.trimIndent())
        }.bodyAsText()
        val segmentId = assertNotNull(extractFieldValue(createBody, "id"), "segmentId")

        val deleteResponse = authClient.delete("/admin/projects/$projectId/segments/$segmentId")
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)
    }

    @Test
    fun `segment analysis returns data`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        assertEquals(HttpStatusCode.OK, authClient.login().status, "login")

        val projectId = assertNotNull(setupProject(authClient), "projectId")

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
        val segmentId = assertNotNull(extractFieldValue(createBody, "id"), "segmentId")

        val analysisResponse = authClient.get("/admin/projects/$projectId/segments/$segmentId/analysis?filter=7d")
        assertEquals(HttpStatusCode.OK, analysisResponse.status)
        val body = analysisResponse.bodyAsText()
        assertTrue(body.contains("segmentId"))
        assertTrue(body.contains("totalViews"))
    }

    /** A project of its own, so counts are not shared with other tests: returns (id, tracking key). */
    private suspend fun newProject(authClient: HttpClient): Pair<String, String> {
        val body = authClient.post("/admin/projects") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Segments ${System.nanoTime()}","domain":"segments-test.com"}""")
        }.bodyAsText()
        return assertNotNull(extractFieldValue(body, "id")) to assertNotNull(extractFieldValue(body, "apiKey"))
    }

    private fun segmentJson(name: String, logic: String) = """
        {"name": "$name", "filters": [
            {"field": "browser", "operator": "contains", "value": "Chrome", "logic": "$logic"},
            {"field": "browser", "operator": "contains", "value": "Firefox", "logic": "AND"}
        ]}
    """.trimIndent()

    @Test
    fun `update segment changes its name and filters, and refuses invalid or unknown segments`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        assertEquals(HttpStatusCode.OK, authClient.login().status, "login")
        val (projectId, _) = newProject(authClient)

        val created = authClient.post("/admin/projects/$projectId/segments") {
            contentType(ContentType.Application.Json)
            setBody(segmentJson("Before edit", "OR"))
        }.bodyAsText()
        val segmentId = assertNotNull(extractFieldValue(created, "id"), "segmentId")

        suspend fun update(id: String, body: String) = authClient.put("/admin/projects/$projectId/segments/$id") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.status

        assertEquals(HttpStatusCode.OK, update(segmentId, segmentJson("After edit", "AND")))
        val listed = authClient.get("/admin/projects/$projectId/segments").bodyAsText()
        assertTrue(listed.contains("After edit") && !listed.contains("Before edit"), listed)
        assertFalse(listed.contains("\"logic\":\"OR\""), "filters replaced: $listed")

        assertEquals(HttpStatusCode.BadRequest, update(segmentId, segmentJson(" ", "AND")), "blank name")
        assertEquals(HttpStatusCode.BadRequest, update(segmentId, segmentJson("x".repeat(101), "AND")), "long name")
        assertEquals(HttpStatusCode.BadRequest, update(segmentId, """{"name":"No filters","filters":[]}"""))
        assertEquals(HttpStatusCode.NotFound, update(java.util.UUID.randomUUID().toString(), segmentJson("Nobody", "AND")))
    }

    @Test
    fun `segment analysis joins filters with the logic stored on the earlier filter`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        assertEquals(HttpStatusCode.OK, authClient.login().status, "login")
        val (projectId, key) = newProject(authClient)

        val agents = listOf(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 " +
                "Safari/605.1.15"
        )
        agents.forEachIndexed { i, agent ->
            val status = client.post("/collect?key=$key") {
                header(HttpHeaders.UserAgent, agent)
                setBody("""{"path":"/","sessionId":"seg-$i","type":"pageview"}""")
            }.status
            assertEquals(HttpStatusCode.Accepted, status)
        }

        suspend fun views(logic: String): String {
            val created = authClient.post("/admin/projects/$projectId/segments") {
                contentType(ContentType.Application.Json)
                setBody(segmentJson("Chrome $logic Firefox", logic))
            }.bodyAsText()
            val id = assertNotNull(extractFieldValue(created, "id"))
            val analysis = authClient.get("/admin/projects/$projectId/segments/$id/analysis?filter=7d").bodyAsText()
            return assertNotNull(Regex("\"totalViews\":(\\d+)").find(analysis), analysis).groupValues[1]
        }

        assertEquals("2", views("OR"), "Chrome or Firefox")
        assertEquals("0", views("AND"), "Chrome and Firefox at once")
    }

    private fun funnelJson(name: String, vararg steps: Triple<String, String, String>) =
        """{"name": "$name", "steps": [""" + steps.joinToString(",") { (stepName, type, match) ->
            """{"name": "$stepName", "stepType": "$type", "matchValue": "$match"}"""
        } + "]}"

    @Test
    fun `update funnel replaces its name and steps, and the analysis follows the new steps`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        assertEquals(HttpStatusCode.OK, authClient.login().status, "login")
        val (projectId, key) = newProject(authClient)

        // One session goes home -> pricing -> signup, one stops at pricing, one only sees home
        val journeys = mapOf(
            "fun-a" to listOf("/" to null, "/pricing" to null, "/pricing" to "signup"),
            "fun-b" to listOf("/" to null, "/pricing" to null),
            "fun-c" to listOf("/" to null)
        )
        for ((session, events) in journeys) for ((path, event) in events) {
            val body = if (event == null) """{"path":"$path","sessionId":"$session","type":"pageview"}"""
            else """{"path":"$path","sessionId":"$session","type":"custom","eventName":"$event"}"""
            assertEquals(HttpStatusCode.Accepted, client.post("/collect?key=$key") { setBody(body) }.status, body)
        }

        val home = Triple("Home", "url", "/")
        val pricing = Triple("Pricing", "url", "/pricing")
        val created = authClient.post("/admin/projects/$projectId/funnels") {
            contentType(ContentType.Application.Json)
            setBody(funnelJson("Before edit", home, pricing))
        }.bodyAsText()
        val funnelId = assertNotNull(extractFieldValue(created, "id"), "funnelId")

        suspend fun update(id: String, body: String) = authClient.put("/admin/projects/$projectId/funnels/$id") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.status
        suspend fun sessionsPerStep() = Regex("\"sessions\":(\\d+)")
            .findAll(authClient.get("/admin/projects/$projectId/funnels/$funnelId/analysis?filter=7d").bodyAsText())
            .map { it.groupValues[1] }.toList()

        assertEquals(listOf("3", "2"), sessionsPerStep())
        assertEquals(HttpStatusCode.OK, update(funnelId, funnelJson("After edit", home, pricing, Triple("Signup", "event", "signup"))))
        val listed = authClient.get("/admin/projects/$projectId/funnels").bodyAsText()
        assertTrue(listed.contains("After edit") && !listed.contains("Before edit"), listed)
        assertEquals(3, Regex("\"stepNumber\"").findAll(listed).count(), "old steps replaced: $listed")
        assertEquals(listOf("3", "2", "1"), sessionsPerStep())

        assertEquals(HttpStatusCode.BadRequest, update(funnelId, funnelJson(" ", home, pricing)), "blank name")
        assertEquals(HttpStatusCode.BadRequest, update(funnelId, funnelJson("x".repeat(101), home, pricing)), "long name")
        assertEquals(HttpStatusCode.BadRequest, update(funnelId, funnelJson("One step", home)), "one step")
        assertEquals(HttpStatusCode.BadRequest, update(funnelId, funnelJson("Blank step", home, Triple(" ", "url", "/x"))))
        assertEquals(HttpStatusCode.BadRequest, update(funnelId, funnelJson("Bad type", home, Triple("X", "click", "/x"))))
        assertEquals(HttpStatusCode.BadRequest,
            update(funnelId, funnelJson("Long match", home, Triple("X", "url", "/" + "x".repeat(512)))))
        assertEquals(HttpStatusCode.NotFound, update(java.util.UUID.randomUUID().toString(), funnelJson("Nobody", home, pricing)))
        assertEquals(listOf("3", "2", "1"), sessionsPerStep(), "refused updates change nothing")
    }

    // ==================== Invalid Project ID Tests ====================

    @Test
    fun `goals endpoint with invalid UUID returns 400`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        assertEquals(HttpStatusCode.OK, authClient.login().status, "login")

        val response = authClient.get("/admin/projects/not-a-uuid/goals")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `funnels endpoint with invalid UUID returns 400`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        assertEquals(HttpStatusCode.OK, authClient.login().status, "login")

        val response = authClient.get("/admin/projects/not-a-uuid/funnels")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `segments endpoint with invalid UUID returns 400`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        assertEquals(HttpStatusCode.OK, authClient.login().status, "login")

        val response = authClient.get("/admin/projects/not-a-uuid/segments")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
