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
    fun `collect accepts text-plain bodies as sent by navigator sendBeacon and ignores unknown keys`() = testApplication {
        application { module() }
        val admin = createAuthClient(); assertEquals(HttpStatusCode.OK, admin.login().status)
        val (projectId, apiKey) = admin.createProject("beacon-${System.nanoTime()}")

        val beacon = admin.post("/collect?key=$apiKey") {
            header(HttpHeaders.UserAgent, chromeUa)
            contentType(ContentType.Text.Plain.withCharset(Charsets.UTF_8))
            setBody(
                """{"path":"/beacon","referrer":null,"sessionId":"abcdef0123456789abcdef0123456789",""" +
                    """"type":"pageview","futureField":1}"""
            )
        }
        assertEquals(HttpStatusCode.Accepted, beacon.status, beacon.bodyAsText())
        assertEquals("/beacon", admin.firstEvent(projectId).str("path"))

        val noType = admin.post("/collect?key=$apiKey") {
            setBody("""{"path":"/no-type","sessionId":"abcdef0123456789abcdef0123456789","type":"pageview"}""")
        }
        assertEquals(HttpStatusCode.Accepted, noType.status, noType.bodyAsText())
        assertEquals(HttpStatusCode.BadRequest, admin.post("/collect?key=$apiKey") { setBody("not json") }.status)
    }

    @Test
    fun `report counts AI-assistant referrals per assistant and as a share of referred visits`() = testApplication {
        application { module() }
        val admin = createAuthClient(); assertEquals(HttpStatusCode.OK, admin.login().status)
        val (projectId, apiKey) = admin.createProject("ai-${System.nanoTime()}")
        fun pv(session: String, referrer: String?) =
            """{"path":"/post","sessionId":"$session","type":"pageview","referrer":${referrer?.let { "\"$it\"" } ?: "null"}}"""
        assertEquals(HttpStatusCode.Accepted, admin.collect(apiKey, pv("s1", "https://chatgpt.com/c/abc")).status)
        assertEquals(HttpStatusCode.Accepted, admin.collect(apiKey, pv("s2", "https://www.perplexity.ai/search")).status)
        assertEquals(HttpStatusCode.Accepted, admin.collect(apiKey, pv("s3", "https://chatgpt.com/")).status)
        assertEquals(HttpStatusCode.Accepted, admin.collect(apiKey, pv("s4", "https://news.ycombinator.com/")).status)
        assertEquals(HttpStatusCode.Accepted, admin.collect(apiKey, pv("s5", null)).status)

        val report = Json.parseToJsonElement(admin.get("/admin/projects/$projectId/report?filter=7d").bodyAsText()).jsonObject
        assertEquals(3L, report["aiReferralVisits"]!!.jsonPrimitive.long)
        assertEquals(4L, report["referredVisits"]!!.jsonPrimitive.long)
        val byAssistant = report["aiReferrals"]!!.jsonArray.map { it.jsonObject }
            .associate { it["label"]!!.jsonPrimitive.content to it["value"]!!.jsonPrimitive.long }
        assertEquals(mapOf("ChatGPT" to 2L, "Perplexity" to 1L), byAssistant)
    }

    @Test
    fun `privacy endpoint reports the running mode, rotation window and retention`() = testApplication {
        application { module() }
        val admin = createAuthClient(); assertEquals(HttpStatusCode.OK, admin.login().status)
        val body = Json.parseToJsonElement(admin.get("/admin/privacy").bodyAsText()).jsonObject
        assertEquals("STANDARD", body["privacyMode"]!!.jsonPrimitive.content)
        assertEquals(24, body["hashRotationHours"]!!.jsonPrimitive.int)
        assertEquals(0, body["dataRetentionDays"]!!.jsonPrimitive.int)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/admin/privacy").status)
    }

    @Test
    fun `page view counts exclude heartbeats, custom and scroll events everywhere`() = testApplication {
        application { module() }
        val admin = createAuthClient(); assertEquals(HttpStatusCode.OK, admin.login().status)
        val (projectId, apiKey) = admin.createProject("views-${System.nanoTime()}")
        val s = "abcdef0123456789abcdef0123456789"
        for (body in listOf(
            """{"path":"/home","sessionId":"$s","type":"pageview"}""",
            """{"path":"/home","sessionId":"$s","type":"heartbeat"}""",
            """{"path":"/home","sessionId":"$s","type":"heartbeat"}""",
            """{"path":"/home","sessionId":"$s","type":"custom","eventName":"signup"}""",
            """{"path":"/home","sessionId":"$s","type":"scroll","scrollDepth":50}""",
        )) assertEquals(HttpStatusCode.Accepted, admin.collect(apiKey, body).status, body)

        val stats = Json.parseToJsonElement(admin.get("/admin/projects/$projectId/stats").bodyAsText()).jsonObject
        assertEquals(1L, stats["totalViews"]!!.jsonPrimitive.long, "stats endpoint")
        assertEquals(1L, stats["topPages"]!!.jsonArray.first().jsonObject["count"]!!.jsonPrimitive.long)

        val comparisonBody = admin.get("/admin/projects/$projectId/report/comparison?filter=7d").bodyAsText()
        val comparison = Json.parseToJsonElement(comparisonBody).jsonObject
        val report = comparison["current"]!!.jsonObject
        assertEquals(1L, report["totalViews"]!!.jsonPrimitive.long, "report")
        assertEquals(1L, report["topPages"]!!.jsonArray.first().jsonObject["value"]!!.jsonPrimitive.long)
        assertEquals(1L, report["totalSessions"]!!.jsonPrimitive.long, "sessions still count every event")
        assertEquals(1L, comparison["timeSeries"]!!.jsonArray.sumOf { it.jsonObject["views"]!!.jsonPrimitive.long }, "time series")
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
