package se.onemanstudio.integration

import io.ktor.client.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import org.junit.Test
import se.onemanstudio.module
import kotlin.test.*

/**
 * Integration tests for behaviour that was previously untested end to end:
 * the three privacy modes, viewer-role restrictions, the JWT token flow,
 * widget key authentication, query-cache invalidation/isolation and the
 * custom-event properties size limit.
 */
class PrivacyRbacAndCacheTest {

    private val chromeUa = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private fun ApplicationTestBuilder.createAuthClient(): HttpClient = createClient { install(HttpCookies) }

    private suspend fun HttpClient.login(username: String = "admin", password: String = "testpassword123"): HttpResponse =
        post("/api/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"$username","password":"$password"}""")
        }

    /** Creates a project and returns (id, apiKey) by reading it back from the list endpoint. */
    private suspend fun HttpClient.createProject(name: String): Pair<String, String> {
        val created = post("/admin/projects") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"$name","domain":"example.com"}""")
        }
        assertEquals(HttpStatusCode.Created, created.status, created.bodyAsText())
        val list = Json.parseToJsonElement(get("/admin/projects").bodyAsText())
        val items = (list as? JsonArray) ?: list.jsonObject["items"]?.jsonArray ?: list.jsonObject["data"]!!.jsonArray
        val project = items.map { it.jsonObject }.first { it["name"]!!.jsonPrimitive.content == name }
        return project["id"]!!.jsonPrimitive.content to project["apiKey"]!!.jsonPrimitive.content
    }

    private suspend fun HttpClient.collect(apiKey: String, body: String, userAgent: String = chromeUa): HttpResponse =
        post("/collect") {
            header("X-Project-Key", apiKey)
            header(HttpHeaders.UserAgent, userAgent)
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private suspend fun HttpClient.firstEvent(projectId: String): JsonObject {
        val body = get("/admin/projects/$projectId/events?page=0&limit=10").bodyAsText()
        return Json.parseToJsonElement(body).jsonObject["events"]!!.jsonArray.first().jsonObject
    }

    private fun JsonObject.str(key: String): String? = this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content

    private fun withPrivacyMode(mode: String, block: () -> Unit) {
        System.setProperty("PRIVACY_MODE", mode)
        try { block() } finally { System.clearProperty("PRIVACY_MODE") }
    }

    private val pageview = """{"path":"/privacy","sessionId":"sess-privacy","type":"pageview","referrer":"https://ref.example.org/post"}"""

    @Test
    fun `STANDARD mode stores browser, OS and device parsed from the User-Agent`() = withPrivacyMode("STANDARD") {
        testApplication {
            application { module() }
            val admin = createAuthClient(); assertEquals(HttpStatusCode.OK, admin.login().status)
            val (projectId, apiKey) = admin.createProject("privacy-standard-${System.nanoTime()}")
            assertEquals(HttpStatusCode.Accepted, admin.collect(apiKey, pageview).status)

            val event = admin.firstEvent(projectId)
            assertEquals("Chrome 120", event.str("browser"))
            assertEquals("Windows", event.str("os"))
            assertEquals("Desktop", event.str("device"))
            assertEquals("https://ref.example.org/post", event.str("referrer"))
        }
    }

    @Test
    fun `STRICT mode keeps browser data but stores no city, region or coordinates`() = withPrivacyMode("STRICT") {
        testApplication {
            application { module() }
            val admin = createAuthClient(); assertEquals(HttpStatusCode.OK, admin.login().status)
            val (projectId, apiKey) = admin.createProject("privacy-strict-${System.nanoTime()}")
            assertEquals(HttpStatusCode.Accepted, admin.collect(apiKey, pageview).status)

            val event = admin.firstEvent(projectId)
            assertEquals("Chrome 120", event.str("browser"))
            assertNull(event.str("city"))
            assertNull(event.str("region"))
            assertNull(event.str("latitude"))
            assertNull(event.str("longitude"))
        }
    }

    @Test
    fun `PARANOID mode stores no browser, OS, device or location at all`() = withPrivacyMode("PARANOID") {
        testApplication {
            application { module() }
            val admin = createAuthClient(); assertEquals(HttpStatusCode.OK, admin.login().status)
            val (projectId, apiKey) = admin.createProject("privacy-paranoid-${System.nanoTime()}")
            assertEquals(HttpStatusCode.Accepted, admin.collect(apiKey, pageview).status)

            val event = admin.firstEvent(projectId)
            for (field in listOf("browser", "os", "device", "country", "city", "region", "latitude", "longitude")) {
                assertNull(event.str(field), "$field should not be stored in PARANOID mode")
            }
            assertEquals("/privacy", event.str("path"), "the page view itself is still recorded")
        }
    }

    @Test
    fun `viewer role is read-only and never sees a real API key`() = testApplication {
        application { module() }
        val admin = createAuthClient(); assertEquals(HttpStatusCode.OK, admin.login().status)
        val (projectId, apiKey) = admin.createProject("rbac-${System.nanoTime()}")
        val viewerName = "viewer-${System.nanoTime()}"
        val created = admin.post("/admin/users") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"$viewerName","password":"viewerpass123","role":"viewer"}""")
        }
        assertEquals(HttpStatusCode.Created, created.status)

        val viewer = createAuthClient(); assertEquals(HttpStatusCode.OK, viewer.login(viewerName, "viewerpass123").status)

        val listBody = viewer.get("/admin/projects").bodyAsText()
        assertTrue(listBody.contains("********"), "API key should be masked for viewers")
        assertFalse(listBody.contains(apiKey), "real API key must not leak to viewers")
        assertEquals(HttpStatusCode.OK, viewer.get("/admin/projects/$projectId/stats").status)

        val mutations = listOf(
            viewer.post("/admin/projects") {
                contentType(ContentType.Application.Json)
                setBody("""{"name":"nope","domain":"nope.com"}""")
            },
            viewer.post("/admin/projects/$projectId/goals") {
                contentType(ContentType.Application.Json)
                setBody("""{"name":"g","goalType":"url","matchValue":"/x"}""")
            },
            viewer.delete("/admin/projects/$projectId"),
            viewer.post("/admin/projects/$projectId/rotate-api-key"),
        )
        mutations.forEachIndexed { i, r ->
            assertEquals(HttpStatusCode.Forbidden, r.status, "mutation #$i should be forbidden for a viewer")
        }
    }

    @Test
    fun `JWT flow issues tokens, authenticates with Bearer, rotates refresh tokens and rejects replay`() = testApplication {
        application { module() }
        val tokenResponse = client.post("/api/token") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"testpassword123"}""")
        }
        assertEquals(HttpStatusCode.OK, tokenResponse.status)
        val tokens = Json.parseToJsonElement(tokenResponse.bodyAsText()).jsonObject
        val access = tokens["accessToken"]!!.jsonPrimitive.content
        val refresh = tokens["refreshToken"]!!.jsonPrimitive.content

        assertEquals(HttpStatusCode.OK, client.get("/admin/projects") { bearerAuth(access) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/admin/projects") { bearerAuth("not-a-token") }.status)

        val rotated = client.post("/api/token/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$refresh"}""")
        }
        assertEquals(HttpStatusCode.OK, rotated.status)
        val newAccess = Json.parseToJsonElement(rotated.bodyAsText()).jsonObject["accessToken"]!!.jsonPrimitive.content
        assertEquals(HttpStatusCode.OK, client.get("/admin/projects") { bearerAuth(newAccess) }.status)

        val replay = client.post("/api/token/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$refresh"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, replay.status, "a used refresh token must not work twice")
    }

    @Test
    fun `widget endpoints reject unknown keys and serve data for a valid key`() = testApplication {
        application { module() }
        val admin = createAuthClient(); assertEquals(HttpStatusCode.OK, admin.login().status)
        val (_, apiKey) = admin.createProject("widget-${System.nanoTime()}")

        assertEquals(HttpStatusCode.NotFound, client.get("/widget/realtime?key=not-a-real-key").status)
        assertEquals(HttpStatusCode.OK, client.get("/widget/realtime?key=$apiKey").status)
        assertEquals(HttpStatusCode.OK, client.get("/widget/sparkline?key=$apiKey").status)
    }

    @Test
    fun `stats cache is invalidated by collect and isolated between projects`() = testApplication {
        application { module() }
        val admin = createAuthClient(); assertEquals(HttpStatusCode.OK, admin.login().status)
        val (projectA, keyA) = admin.createProject("cache-a-${System.nanoTime()}")
        val (projectB, keyB) = admin.createProject("cache-b-${System.nanoTime()}")
        suspend fun views(id: String): Long =
            Json.parseToJsonElement(admin.get("/admin/projects/$id/stats").bodyAsText()).jsonObject["totalViews"]!!.jsonPrimitive.long

        assertEquals(0L, views(projectA)); assertEquals(0L, views(projectB)) // both now cached
        assertEquals(HttpStatusCode.Accepted, admin.collect(keyA, pageview).status)
        assertEquals(1L, views(projectA), "collect must invalidate the cached stats")
        assertEquals(0L, views(projectB), "project B's cache entry must be untouched")
        assertEquals(HttpStatusCode.Accepted, admin.collect(keyB, pageview).status)
        assertEquals(1L, views(projectB)); assertEquals(1L, views(projectA))
    }

    @Test
    fun `collect enforces the 2048 character limit on custom event properties`() = testApplication {
        application { module() }
        val admin = createAuthClient(); assertEquals(HttpStatusCode.OK, admin.login().status)
        val (_, apiKey) = admin.createProject("props-${System.nanoTime()}")
        fun custom(size: Int) = buildJsonObject {
            put("path", "/buy"); put("sessionId", "sess-props"); put("type", "custom"); put("eventName", "purchase")
            put("properties", buildJsonObject { put("note", "x".repeat(size)) }.toString())
        }.toString()

        assertEquals(HttpStatusCode.Accepted, admin.collect(apiKey, custom(2000)).status)
        assertEquals(HttpStatusCode.BadRequest, admin.collect(apiKey, custom(2100)).status)
    }
}
