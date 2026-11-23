package se.onemanstudio.integration

import io.ktor.client.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.After
import org.junit.Test
import se.onemanstudio.module
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `/metrics` answers to a signed-in admin or to `Authorization: Bearer <METRICS_TOKEN>`, and to nobody else. */
class MetricsEndpointTest {

    @After
    fun clearToken() {
        System.clearProperty("METRICS_TOKEN")
    }

    private suspend fun HttpClient.login(username: String, password: String) = post("/api/login") {
        contentType(ContentType.Application.Json)
        setBody("""{"username":"$username","password":"$password"}""")
    }

    @Test
    fun `without a session or token it is refused`() = testApplication {
        application { module() }

        assertEquals(HttpStatusCode.Unauthorized, client.get("/metrics").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/metrics") {
            header(HttpHeaders.Authorization, "Bearer ")
        }.status, "an empty token never matches, set or not")
    }

    @Test
    fun `a signed-in admin can read it, a viewer cannot`() = testApplication {
        application { module() }
        val admin = createClient { install(HttpCookies) }
        assertEquals(HttpStatusCode.OK, admin.login("admin", "testpassword123").status)

        val ok = admin.get("/metrics")
        assertEquals(HttpStatusCode.OK, ok.status)
        assertTrue(ok.bodyAsText().contains("mini_numbers_uptime_seconds"))

        val user = "viewer${System.nanoTime()}"
        assertEquals(HttpStatusCode.Created, admin.post("/admin/users") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"$user","password":"viewerpassword123","role":"viewer"}""")
        }.status)
        val viewer = createClient { install(HttpCookies) }
        assertEquals(HttpStatusCode.OK, viewer.login(user, "viewerpassword123").status)
        assertEquals(HttpStatusCode.Unauthorized, viewer.get("/metrics").status)
    }

    @Test
    fun `the metrics token is accepted only when it matches exactly`() = testApplication {
        System.setProperty("METRICS_TOKEN", "scrape-token-1234567890")
        application { module() }

        val ok = client.get("/metrics") { header(HttpHeaders.Authorization, "Bearer scrape-token-1234567890") }
        assertEquals(HttpStatusCode.OK, ok.status)
        assertTrue(ok.bodyAsText().contains("mini_numbers_uptime_seconds"))
        for (bad in listOf("Bearer scrape-token-123456789", "Bearer scrape-token-12345678901", "Bearer other", "scrape-token-1234567890")) {
            assertEquals(HttpStatusCode.Unauthorized, client.get("/metrics") {
                header(HttpHeaders.Authorization, bad)
            }.status, bad)
        }
    }

    @Test
    fun `a token that is not configured opens nothing`() = testApplication {
        application { module() }

        assertEquals(HttpStatusCode.Unauthorized, client.get("/metrics") {
            header(HttpHeaders.Authorization, "Bearer scrape-token-1234567890")
        }.status)
    }
}
