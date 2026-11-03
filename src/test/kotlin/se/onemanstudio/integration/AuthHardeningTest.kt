package se.onemanstudio.integration

import io.ktor.client.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.Test
import io.ktor.server.sessions.*
import se.onemanstudio.config.ConfigLoader
import se.onemanstudio.db.RefreshTokens
import se.onemanstudio.db.Users
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

    @Test
    fun `me and privacy sit behind the Origin allowlist like the rest of the admin API`() {
        System.setProperty("ALLOWED_ORIGINS", "https://dashboard.example.com")
        try {
            testApplication {
                application { module() }
                val authClient = createAuthClient()
                assertEquals(HttpStatusCode.OK, authClient.login("admin", "testpassword123").status)
                for (path in listOf("/admin/me", "/admin/privacy")) {
                    assertEquals(HttpStatusCode.Forbidden,
                        authClient.get(path) { header(HttpHeaders.Origin, "https://evil.example.com") }.status, path)
                    assertEquals(HttpStatusCode.OK,
                        authClient.get(path) { header(HttpHeaders.Origin, "https://dashboard.example.com") }.status, path)
                }
            }
        } finally {
            System.clearProperty("ALLOWED_ORIGINS")
        }
    }

    @Test
    fun `me reports the role as it is now, for the session and for a token issued before a demotion`() = testApplication {
        application { module() }
        val admin = createAuthClient()
        assertEquals(HttpStatusCode.OK, admin.login("admin", "testpassword123").status)
        val username = "admin-me-${System.nanoTime()}"
        val userId = admin.createUser(username, "secondadmin123", "admin")

        val second = createAuthClient()
        assertEquals(HttpStatusCode.OK, second.login(username, "secondadmin123").status)
        val tokenResponse = second.post("/api/token") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"$username","password":"secondadmin123"}""")
        }
        assertEquals(HttpStatusCode.OK, tokenResponse.status)
        val token = Regex("\"accessToken\":\"([^\"]+)\"").find(tokenResponse.bodyAsText())!!.groupValues[1]
        suspend fun role(viaToken: Boolean): String {
            val body = if (viaToken) client.get("/api/me") { bearerAuth(token) }.bodyAsText() else second.get("/admin/me").bodyAsText()
            return Regex("\"role\":\"([^\"]+)\"").find(body)!!.groupValues[1]
        }
        assertEquals("admin", role(viaToken = false))
        assertEquals("admin", role(viaToken = true))

        val demote = admin.put("/admin/users/$userId/role") {
            contentType(ContentType.Application.Json)
            setBody("""{"role":"viewer"}""")
        }
        assertEquals(HttpStatusCode.OK, demote.status)
        assertEquals("viewer", role(viaToken = false), "session cookie still says admin, the answer must not")
        assertEquals("viewer", role(viaToken = true), "token claim still says admin, the answer must not")
    }

    private suspend fun HttpClient.setActive(userId: String, active: Boolean): HttpStatusCode =
        put("/admin/users/$userId/active") {
            contentType(ContentType.Application.Json)
            setBody("""{"isActive":$active}""")
        }.status

    private suspend fun HttpClient.seededAdminId(): String =
        Regex("\"id\":\"([^\"]+)\",\"username\":\"admin\"").find(get("/admin/users").bodyAsText())!!.groupValues[1]

    private fun refreshTokensFor(username: String): Long =
        transaction { RefreshTokens.selectAll().where { RefreshTokens.username eq username }.count() }

    @Test
    fun `deactivating a user ends their session, revokes their tokens and blocks login until reactivated`() = testApplication {
        application { module() }
        val admin = createAuthClient()
        assertEquals(HttpStatusCode.OK, admin.login("admin", "testpassword123").status)
        val username = "viewer-off-${System.nanoTime()}"
        val userId = admin.createUser(username, "viewerpass123", "viewer")

        val viewer = createAuthClient()
        assertEquals(HttpStatusCode.OK, viewer.login(username, "viewerpass123").status)
        assertEquals(HttpStatusCode.OK, viewer.post("/api/token") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"$username","password":"viewerpass123"}""")
        }.status)
        assertEquals(1L, refreshTokensFor(username))
        assertEquals(HttpStatusCode.OK, viewer.get("/admin/projects").status)

        assertEquals(HttpStatusCode.OK, admin.setActive(userId, false))
        assertEquals(HttpStatusCode.Unauthorized, viewer.get("/admin/projects").status, "live session ends")
        assertEquals(HttpStatusCode.Unauthorized, viewer.login(username, "viewerpass123").status, "login refused")
        assertEquals(0L, refreshTokensFor(username), "refresh tokens revoked")

        assertEquals(HttpStatusCode.OK, admin.setActive(userId, true))
        assertEquals(HttpStatusCode.OK, viewer.login(username, "viewerpass123").status, "login works again")
    }

    @Test
    fun `a deactivated env admin cannot sign in through the config password fallback`() = testApplication {
        application { module() }
        val admin = createAuthClient()
        assertEquals(HttpStatusCode.OK, admin.login("admin", "testpassword123").status)
        val seedId = admin.seededAdminId()
        val secondName = "admin-b-${System.nanoTime()}"
        admin.createUser(secondName, "secondadmin123", "admin")
        val second = createAuthClient()
        assertEquals(HttpStatusCode.OK, second.login(secondName, "secondadmin123").status)
        try {
            assertEquals(HttpStatusCode.OK, second.setActive(seedId, false))
            assertEquals(HttpStatusCode.Unauthorized, createAuthClient().login("admin", "testpassword123").status,
                "the .env password must not reopen a deactivated account")
        } finally {
            transaction { Users.update({ Users.username eq "admin" }) { it[isActive] = true } }
        }
    }

    @Test
    fun `an admin cannot demote, deactivate or delete their own account`() = testApplication {
        application { module() }
        val admin = createAuthClient()
        assertEquals(HttpStatusCode.OK, admin.login("admin", "testpassword123").status)
        val seedId = admin.seededAdminId()
        assertEquals(HttpStatusCode.Conflict, admin.put("/admin/users/$seedId/role") {
            contentType(ContentType.Application.Json)
            setBody("""{"role":"viewer"}""")
        }.status)
        assertEquals(HttpStatusCode.Conflict, admin.setActive(seedId, false))
        assertEquals(HttpStatusCode.Conflict, admin.delete("/admin/users/$seedId").status)
        assertEquals(HttpStatusCode.OK, admin.get("/admin/projects").status, "still an active admin")
    }

    @Test
    fun `deleting a user removes their refresh tokens`() = testApplication {
        application { module() }
        val admin = createAuthClient()
        assertEquals(HttpStatusCode.OK, admin.login("admin", "testpassword123").status)
        val username = "viewer-gone-${System.nanoTime()}"
        val userId = admin.createUser(username, "viewerpass123", "viewer")
        assertEquals(HttpStatusCode.OK, client.post("/api/token") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"$username","password":"viewerpass123"}""")
        }.status)
        assertEquals(1L, refreshTokensFor(username))
        assertEquals(HttpStatusCode.NoContent, admin.delete("/admin/users/$userId").status)
        assertEquals(0L, refreshTokensFor(username))
    }

    private suspend fun HttpClient.refreshTokenFor(username: String, password: String): String {
        val response = post("/api/token") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"$username","password":"$password"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        return Regex("\"refreshToken\":\"([^\"]+)\"").find(response.bodyAsText())!!.groupValues[1]
    }

    private suspend fun HttpClient.refresh(token: String): HttpResponse = post("/api/token/refresh") {
        contentType(ContentType.Application.Json)
        setBody("""{"refreshToken":"$token"}""")
    }

    @Test
    fun `logging out ends the user's sessions on every device`() = testApplication {
        application { module() }
        val admin = createAuthClient()
        assertEquals(HttpStatusCode.OK, admin.login("admin", "testpassword123").status)
        val username = "viewer-two-${System.nanoTime()}"
        admin.createUser(username, "viewerpass123", "viewer")

        val phone = createAuthClient()
        val laptop = createAuthClient()
        assertEquals(HttpStatusCode.OK, phone.login(username, "viewerpass123").status)
        assertEquals(HttpStatusCode.OK, laptop.login(username, "viewerpass123").status)
        assertEquals(HttpStatusCode.OK, laptop.get("/admin/projects").status)

        assertEquals(HttpStatusCode.OK, phone.post("/api/logout").status)
        assertEquals(HttpStatusCode.Unauthorized, laptop.get("/admin/projects").status, "the other device is signed out too")
        assertEquals(HttpStatusCode.OK, laptop.login(username, "viewerpass123").status)
        assertEquals(HttpStatusCode.OK, laptop.get("/admin/projects").status, "a fresh login works")
    }

    @Test
    fun `a replayed refresh token revokes its whole family`() = testApplication {
        application { module() }
        val admin = createAuthClient()
        assertEquals(HttpStatusCode.OK, admin.login("admin", "testpassword123").status)
        val username = "viewer-replay-${System.nanoTime()}"
        admin.createUser(username, "viewerpass123", "viewer")

        val first = client.refreshTokenFor(username, "viewerpass123")
        val rotated = client.refresh(first)
        assertEquals(HttpStatusCode.OK, rotated.status)
        val second = Regex("\"refreshToken\":\"([^\"]+)\"").find(rotated.bodyAsText())!!.groupValues[1]

        assertEquals(HttpStatusCode.Unauthorized, client.refresh(first).status, "a used token is refused")
        assertEquals(HttpStatusCode.Unauthorized, client.refresh(second).status, "its successor is revoked by the replay")
    }

    @Test
    fun `password reset signs the env admin out everywhere and leaves other users' tokens alone`() = testApplication {
        application { module() }
        val admin = createAuthClient()
        assertEquals(HttpStatusCode.OK, admin.login("admin", "testpassword123").status)
        val viewerName = "viewer-keep-${System.nanoTime()}"
        admin.createUser(viewerName, "viewerpass123", "viewer")
        client.refreshTokenFor(viewerName, "viewerpass123")
        client.refreshTokenFor("admin", "testpassword123")
        assertTrue(refreshTokensFor("admin") >= 1L)

        val salt = ConfigLoader.load().security.serverSalt
        suspend fun reset(current: String, new: String): HttpStatusCode = client.post("/api/password-reset") {
            contentType(ContentType.Application.Json)
            setBody("""{"currentPassword":"$current","newPassword":"$new","serverSalt":"$salt"}""")
        }.status
        try {
            assertEquals(HttpStatusCode.OK, reset("testpassword123", "temporary-pass-123"))
            assertEquals(HttpStatusCode.Unauthorized, admin.get("/admin/projects").status, "the admin's cookie session is gone")
            assertEquals(0L, refreshTokensFor("admin"), "the admin's refresh tokens are revoked")
            assertEquals(1L, refreshTokensFor(viewerName), "other users keep theirs")
            assertEquals(HttpStatusCode.OK, createAuthClient().login("admin", "temporary-pass-123").status)
        } finally {
            reset("temporary-pass-123", "testpassword123")
        }
    }

    private fun forgedCookieFor(username: String, role: String = "admin"): String =
        defaultSessionSerializer<UserSession>()
            .serialize(UserSession(username = username, role = role, createdAt = System.currentTimeMillis()))

    /** A genuinely signed cookie whose creation time we choose. */
    private fun signedCookieCreatedAt(username: String, createdAt: Long): String =
        SessionSigner.transformWrite(
            defaultSessionSerializer<UserSession>().serialize(UserSession(username, "viewer", createdAt)))

    @Test
    fun `the cookie carries its creation time, so the 4-hour session lifetime applies`() = testApplication {
        application { module() }
        val admin = createAuthClient()
        assertEquals(HttpStatusCode.OK, admin.login("admin", "testpassword123").status)
        val username = "viewer-aged-${System.nanoTime()}" // a fresh user has no logout cut-off to interfere
        admin.createUser(username, "viewerpass123", "viewer")
        val hour = 60 * 60 * 1000L
        suspend fun withSessionAged(age: Long): HttpStatusCode = client.get("/admin/projects") {
            cookie("mini_numbers_session", signedCookieCreatedAt(username, System.currentTimeMillis() - age))
        }.status
        assertEquals(HttpStatusCode.OK, withSessionAged(hour))
        assertEquals(HttpStatusCode.Unauthorized, withSessionAged(5 * hour), "a five-hour-old session must be rejected")
    }

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
