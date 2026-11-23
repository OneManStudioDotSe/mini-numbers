package se.onemanstudio.integration

import io.ktor.client.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.Test
import se.onemanstudio.module
import java.util.UUID
import kotlin.test.*

/**
 * Integration tests for webhook API endpoints
 * Tests CRUD operations, URL validation, and test delivery
 */
class WebhookEndpointTest {

    private fun ApplicationTestBuilder.createAuthClient(): HttpClient {
        return createClient {
            install(HttpCookies)
        }
    }

    private suspend fun HttpClient.login(
        username: String = "admin",
        password: String = "testpassword123"
    ): HttpResponse {
        return post("/api/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"$username","password":"$password"}""")
        }
    }

    @Test
    fun `create webhook with HTTP URL returns 400`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        val loginResponse = authClient.login()

        assertEquals(HttpStatusCode.OK, loginResponse.status, "login")

        // First create a project to get a valid project ID
        val createProjectResponse = authClient.post("/admin/projects") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Webhook Test","domain":"webhook-test.com"}""")
        }

        assertEquals(HttpStatusCode.Created, createProjectResponse.status, "create project")
        val projectBody = createProjectResponse.bodyAsText()
        val projectId = assertNotNull(Regex(""""id"\s*:\s*"([^"]+)"""").find(projectBody)?.groupValues?.get(1), "projectId")

        val response = authClient.post("/admin/projects/$projectId/webhooks") {
            contentType(ContentType.Application.Json)
            setBody("""{"url":"http://insecure.example.com/webhook","events":["goal_conversion"]}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("HTTPS"))
    }

    @Test
    fun `create webhook with blank URL returns 400`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        val loginResponse = authClient.login()

        assertEquals(HttpStatusCode.OK, loginResponse.status, "login")

        val createProjectResponse = authClient.post("/admin/projects") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Webhook Test 2","domain":"webhook-test2.com"}""")
        }

        assertEquals(HttpStatusCode.Created, createProjectResponse.status, "create project")
        val projectBody = createProjectResponse.bodyAsText()
        val projectId = assertNotNull(Regex(""""id"\s*:\s*"([^"]+)"""").find(projectBody)?.groupValues?.get(1), "projectId")

        val response = authClient.post("/admin/projects/$projectId/webhooks") {
            contentType(ContentType.Application.Json)
            setBody("""{"url":"","events":["goal_conversion"]}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `create webhook refuses unknown events, no events and addresses it could not call`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        assertEquals(HttpStatusCode.OK, authClient.login().status, "login")
        val projectBody = authClient.post("/admin/projects") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Webhook validation","domain":"webhook-validation.com"}""")
        }.bodyAsText()
        val projectId = assertNotNull(Regex(""""id"\s*:\s*"([^"]+)"""").find(projectBody)?.groupValues?.get(1), "projectId")

        suspend fun create(body: String) = authClient.post("/admin/projects/$projectId/webhooks") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.status
        val url = "https://hooks.example.com/in"

        assertEquals(HttpStatusCode.BadRequest, create("""{"url":"$url","events":["goal_conversion","page_view"]}"""))
        assertEquals(HttpStatusCode.BadRequest, create("""{"url":"$url","events":[]}"""), "no events")
        assertEquals(HttpStatusCode.BadRequest, create("""{"url":"https://exa mple.com/in","events":["goal_conversion"]}"""))
        assertEquals(HttpStatusCode.BadRequest, create("""{"url":"https://","events":["goal_conversion"]}"""), "no host")
        assertEquals(HttpStatusCode.BadRequest,
            create("""{"url":"$url/${"x".repeat(1024)}","events":["goal_conversion"]}"""), "long address")
        for (host in listOf("169.254.169.254", "169.254.0.1", "[fe80::1]", "[::ffff:169.254.169.254]")) {
            val body = """{"url":"https://$host/latest","events":["goal_conversion"]}"""
            assertEquals(HttpStatusCode.BadRequest, create(body), "link-local $host")
        }
        assertEquals("[]", authClient.get("/admin/projects/$projectId/webhooks").bodyAsText(), "nothing was stored")

        for (allowed in listOf("127.0.0.1:9000", "192.168.1.10")) {
            assertEquals(HttpStatusCode.Created, create("""{"url":"https://$allowed/in","events":["traffic_spike"]}"""), allowed)
        }
        assertEquals(HttpStatusCode.Created, create("""{"url":"$url","events":["traffic_spike","traffic_spike"]}"""))
        val listed = authClient.get("/admin/projects/$projectId/webhooks").bodyAsText()
        assertTrue(listed.contains(""""events":["traffic_spike"]"""), "duplicates collapse: $listed")
    }

    @Test
    fun `a webhook can be paused and resumed`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        assertEquals(HttpStatusCode.OK, authClient.login().status, "login")
        val createProjectResponse = authClient.post("/admin/projects") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Webhook Pause","domain":"webhook-pause.com"}""")
        }
        assertEquals(HttpStatusCode.Created, createProjectResponse.status, "create project")
        val projectId = assertNotNull(Regex(""""id"\s*:\s*"([^"]+)"""").find(createProjectResponse.bodyAsText())?.groupValues?.get(1))
        val created = authClient.post("/admin/projects/$projectId/webhooks") {
            contentType(ContentType.Application.Json)
            setBody("""{"url":"https://hooks.example.com/in","events":["goal_conversion"]}""")
        }
        assertEquals(HttpStatusCode.Created, created.status, created.bodyAsText())
        val webhookId = assertNotNull(Regex(""""id"\s*:\s*"([^"]+)"""").find(created.bodyAsText())?.groupValues?.get(1))

        suspend fun setActive(active: Boolean): HttpStatusCode = authClient.put("/admin/projects/$projectId/webhooks/$webhookId") {
            contentType(ContentType.Application.Json)
            setBody("""{"isActive":$active}""")
        }.status
        suspend fun listedAsActive(): Boolean {
            val listing = authClient.get("/admin/projects/$projectId/webhooks").bodyAsText()
            return Regex(""""isActive"\s*:\s*(true|false)""").find(listing)!!.groupValues[1] == "true"
        }

        assertTrue(listedAsActive())
        assertEquals(HttpStatusCode.OK, setActive(false))
        assertFalse(listedAsActive(), "paused")
        assertEquals(HttpStatusCode.OK, setActive(true))
        assertTrue(listedAsActive(), "resumed")
        assertEquals(HttpStatusCode.NotFound, authClient.put("/admin/projects/$projectId/webhooks/${UUID.randomUUID()}") {
            contentType(ContentType.Application.Json)
            setBody("""{"isActive":false}""")
        }.status)
    }

    @Test
    fun `delete nonexistent webhook returns 404`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        val loginResponse = authClient.login()

        assertEquals(HttpStatusCode.OK, loginResponse.status, "login")

        val createProjectResponse = authClient.post("/admin/projects") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Webhook Test 3","domain":"webhook-test3.com"}""")
        }

        assertEquals(HttpStatusCode.Created, createProjectResponse.status, "create project")
        val projectBody = createProjectResponse.bodyAsText()
        val projectId = assertNotNull(Regex(""""id"\s*:\s*"([^"]+)"""").find(projectBody)?.groupValues?.get(1), "projectId")

        val response = authClient.delete("/admin/projects/$projectId/webhooks/00000000-0000-0000-0000-000000000000")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `webhook endpoint with invalid project ID returns 400`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        val loginResponse = authClient.login()

        assertEquals(HttpStatusCode.OK, loginResponse.status, "login")

        val response = authClient.get("/admin/projects/not-a-uuid/webhooks")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `list deliveries for nonexistent webhook returns 404`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        val loginResponse = authClient.login()

        assertEquals(HttpStatusCode.OK, loginResponse.status, "login")

        val createProjectResponse = authClient.post("/admin/projects") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Webhook Test 4","domain":"webhook-test4.com"}""")
        }

        assertEquals(HttpStatusCode.Created, createProjectResponse.status, "create project")
        val projectBody = createProjectResponse.bodyAsText()
        val projectId = assertNotNull(Regex(""""id"\s*:\s*"([^"]+)"""").find(projectBody)?.groupValues?.get(1), "projectId")

        val response = authClient.get("/admin/projects/$projectId/webhooks/00000000-0000-0000-0000-000000000000/deliveries")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
