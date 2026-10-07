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
 * Integration tests for the auth hardening wired in M1:
 * rate limiting on credential endpoints, the admin Origin allowlist,
 * and immediate effect of role changes / user deletion on live sessions.
 */
class AuthHardeningTest {

    private fun ApplicationTestBuilder.createAuthClient(): HttpClient = createClient { install(HttpCookies) }

    private suspend fun HttpClient.login(username: String, password: String): HttpResponse =
        post("/api/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"$username","password":"$password"}""")
        }

    private suspend fun HttpClient.createUser(username: String, password: String, role: String): String {
        val response = post("/admin/users") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"$username","password":"$password","role":"$role"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status, "user creation failed: ${response.bodyAsText()}")
        return Regex("\"id\":\"([^\"]+)\"").find(response.bodyAsText())!!.groupValues[1]
    }

    @Test
    fun `login endpoint returns 429 after 20 attempts per minute from one IP`() = testApplication {
        application { module() }
        val statuses = (1..21).map {
            client.login("nobody-${System.nanoTime()}", "wrong-password").status
        }
        assertTrue(statuses.take(20).all { it == HttpStatusCode.Unauthorized }, "first 20 attempts: $statuses")
        assertEquals(HttpStatusCode.TooManyRequests, statuses.last())
    }

    @Test
    fun `admin API rejects cross-origin requests not in ALLOWED_ORIGINS but accepts same-host and listed origins`() {
        System.setProperty("ALLOWED_ORIGINS", "https://dashboard.example.com")
        try {
            testApplication {
                application { module() }
                val authClient = createAuthClient()
                assertEquals(HttpStatusCode.OK, authClient.login("admin", "testpassword123").status)

                suspend fun createProject(origin: String?): HttpStatusCode = authClient.post("/admin/projects") {
                    contentType(ContentType.Application.Json)
                    if (origin != null) header(HttpHeaders.Origin, origin)
                    setBody("""{"name":"cors-${System.nanoTime()}","domain":"example.com"}""")
                }.status

                assertEquals(HttpStatusCode.Forbidden, createProject("https://evil.example.com"))
                assertEquals(HttpStatusCode.Created, createProject("https://dashboard.example.com"))
                assertEquals(HttpStatusCode.Created, createProject("http://localhost"))
                assertEquals(HttpStatusCode.Created, createProject(null))
            }
        } finally {
            System.clearProperty("ALLOWED_ORIGINS")
        }
    }

    @Test
    fun `deleting a user invalidates their live session on the next request`() = testApplication {
        application { module() }
        val admin = createAuthClient()
        assertEquals(HttpStatusCode.OK, admin.login("admin", "testpassword123").status)
        val username = "viewer-del-${System.nanoTime()}"
        val userId = admin.createUser(username, "viewerpass123", "viewer")

        val viewer = createAuthClient()
        assertEquals(HttpStatusCode.OK, viewer.login(username, "viewerpass123").status)
        assertEquals(HttpStatusCode.OK, viewer.get("/admin/projects").status)

        val deleted = admin.delete("/admin/users/$userId").status
        assertTrue(deleted == HttpStatusCode.OK || deleted == HttpStatusCode.NoContent, "delete returned $deleted")
        assertEquals(HttpStatusCode.Unauthorized, viewer.get("/admin/projects").status)
    }

    @Test
    fun `demoting an admin to viewer blocks mutations on their live session`() = testApplication {
        application { module() }
        val admin = createAuthClient()
        assertEquals(HttpStatusCode.OK, admin.login("admin", "testpassword123").status)
        val username = "admin-demote-${System.nanoTime()}"
        val userId = admin.createUser(username, "secondadmin123", "admin")

        val second = createAuthClient()
        assertEquals(HttpStatusCode.OK, second.login(username, "secondadmin123").status)
        suspend fun createProject(): HttpStatusCode = second.post("/admin/projects") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"demote-${System.nanoTime()}","domain":"example.com"}""")
        }.status
        assertEquals(HttpStatusCode.Created, createProject())

        val demote = admin.put("/admin/users/$userId/role") {
            contentType(ContentType.Application.Json)
            setBody("""{"role":"viewer"}""")
        }
        assertEquals(HttpStatusCode.OK, demote.status)
        assertEquals(HttpStatusCode.Forbidden, createProject())
        assertEquals(HttpStatusCode.OK, second.get("/admin/projects").status, "viewer keeps read access")
    }
}
