package se.onemanstudio.integration

import io.ktor.client.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.Test
import io.ktor.server.sessions.*
import se.onemanstudio.core.JwtService
import se.onemanstudio.core.SessionSigner
import se.onemanstudio.core.models.UserSession
import se.onemanstudio.module
import kotlin.test.*

/**
 * Integration tests for the auth hardening wired in M1:
 * rate limiting on credential endpoints, the admin Origin allowlist,
 * immediate effect of role changes / user deletion on live sessions, and the signed
 * session cookie (audit items C1 and C3).
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

    private fun forgedCookieFor(username: String, role: String = "admin"): String =
        defaultSessionSerializer<UserSession>().serialize(UserSession(username = username, role = role))

    @Test
    fun `a hand-made unsigned session cookie is rejected`() = testApplication {
        application { module() }
        val response = client.get("/admin/projects") { cookie("mini_numbers_session", forgedCookieFor("admin")) }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `a session cookie with a tampered payload is rejected even with a genuine signature`() = testApplication {
        application { module() }
        val admin = createAuthClient()
        assertEquals(HttpStatusCode.OK, admin.login("admin", "testpassword123").status)
        val username = "viewer-${System.nanoTime()}"
        admin.createUser(username, "viewerpass123", "viewer")

        val viewerLogin = client.login(username, "viewerpass123")
        assertEquals(HttpStatusCode.OK, viewerLogin.status)
        val signed = viewerLogin.setCookie().first { it.name == "mini_numbers_session" }.value
            .let { if (it.contains('%')) it.decodeURLQueryComponent(plusIsSpace = true) else it }
        val mac = signed.substringAfterLast('/')
        assertTrue(mac.matches(Regex("[0-9a-f]{64}")), "signed cookie should end in a 64-hex mac: $signed")

        assertEquals(HttpStatusCode.OK, client.get("/admin/projects") { cookie("mini_numbers_session", signed) }.status,
            "the genuine cookie still works")
        val tampered = client.get("/admin/projects") { cookie("mini_numbers_session", "${forgedCookieFor("admin")}/$mac") }
        assertEquals(HttpStatusCode.Unauthorized, tampered.status, "viewer's mac on an admin payload")
    }

    @Test
    fun `session cookie carries the expected attributes`() {
        testApplication {
            application { module() }
            val cookie = client.login("admin", "testpassword123").setCookie().first { it.name == "mini_numbers_session" }
            assertTrue(cookie.httpOnly)
            assertEquals("Strict", cookie.extensions["SameSite"])
            assertEquals("/", cookie.path)
            assertEquals(60 * 60 * 24 * 7, cookie.maxAge)
            assertFalse(cookie.secure, "not Secure in development mode (the test default)")
        }
        System.setProperty("KTOR_DEVELOPMENT", "false")
        try {
            testApplication {
                application { module() }
                val cookie = client.login("admin", "testpassword123").setCookie().first { it.name == "mini_numbers_session" }
                assertTrue(cookie.secure, "Secure outside development mode")
            }
        } finally {
            System.setProperty("KTOR_DEVELOPMENT", "true")
        }
    }

    @Test
    fun `JWT verifier and session signer follow a key change without reinstalling the plugins`() = testApplication {
        // Models the setup wizard: the auth plugins are installed before the salt exists
        application { module() }
        client.get("/health") // the test app starts (and installs the auth plugins) on the first request
        val originalSalt = System.getProperty("SERVER_SALT")
        try {
            val wizardSalt = "wizard-salt-" + "x".repeat(40)
            JwtService.init(wizardSalt)
            SessionSigner.init(wizardSalt, secureCookie = true) // the wizard said production

            val tokenResponse = client.post("/api/token") {
                contentType(ContentType.Application.Json)
                setBody("""{"username":"admin","password":"testpassword123"}""")
            }
            assertEquals(HttpStatusCode.OK, tokenResponse.status)
            val token = Regex("\"accessToken\":\"([^\"]+)\"").find(tokenResponse.bodyAsText())!!.groupValues[1]
            assertEquals(HttpStatusCode.OK, client.get("/admin/projects") { bearerAuth(token) }.status,
                "a token signed with the new key must verify")

            val login = client.login("admin", "testpassword123")
            assertEquals(HttpStatusCode.OK, login.status)
            val cookie = login.setCookie().first { it.name == "mini_numbers_session" }
            assertTrue(cookie.secure, "the Secure flag follows the wizard's choice without a restart")
            assertEquals(HttpStatusCode.OK, client.get("/admin/projects") { cookie("mini_numbers_session", cookie.value) }.status,
                "a cookie signed with the new key must verify")
        } finally {
            JwtService.init(originalSalt)
            SessionSigner.init(originalSalt, secureCookie = false)
        }
    }
}
