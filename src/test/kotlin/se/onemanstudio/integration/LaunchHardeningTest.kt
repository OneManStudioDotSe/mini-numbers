package se.onemanstudio.integration

import io.ktor.client.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.delay
import org.junit.Test
import org.slf4j.LoggerFactory
import se.onemanstudio.api.models.collection.PageViewPayload
import se.onemanstudio.config.ConfigLoader
import se.onemanstudio.core.verifyCredentials
import se.onemanstudio.middleware.InputValidator
import se.onemanstudio.middleware.MAX_REQUEST_BODY_BYTES
import se.onemanstudio.module
import se.onemanstudio.setup.SetupToken
import se.onemanstudio.setup.setupSaveRefusal
import kotlin.test.*

/**
 * The fixes from the launch audit of 2026-11-14: request body limit, the setup-save guard and
 * token, lockout per address, API tokens ending with logout and password change, stricter
 * `/collect` validation and crawlers left out of the numbers.
 */
class LaunchHardeningTest {

    private fun ApplicationTestBuilder.createAuthClient(): HttpClient = createClient { install(HttpCookies) }

    private suspend fun HttpClient.login(username: String, password: String): HttpResponse =
        post("/api/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"$username","password":"$password"}""")
        }

    private suspend fun HttpClient.createUser(username: String, password: String, role: String) {
        val response = post("/admin/users") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"$username","password":"$password","role":"$role"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status, "user creation failed: ${response.bodyAsText()}")
    }

    private fun field(json: String, name: String): String =
        Regex("\"$name\":\"([^\"]+)\"").find(json)?.groupValues?.get(1) ?: fail("no $name in $json")

    @Test
    fun `a request body over the limit is refused with 413 on public endpoints`() = testApplication {
        application { module() }
        val big = "x".repeat(MAX_REQUEST_BODY_BYTES.toInt() + 1)

        val collect = client.post("/collect?key=anything") {
            setBody("""{"path":"/","sessionId":"s1","type":"pageview","properties":"$big"}""")
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, collect.status)

        val login = client.post("/api/login") {
            contentType(ContentType.Application.Json)
            setBody(big)
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, login.status)
    }

    @Test
    fun `setup save is refused once a configuration exists, and without the token before that`() {
        assertNotNull(setupSaveRefusal(configExists = true, tokenMatches = true), "config exists (also after a failed boot)")
        assertNotNull(setupSaveRefusal(configExists = false, tokenMatches = false), "fresh install, wrong token")
        assertNull(setupSaveRefusal(configExists = false, tokenMatches = true))

        assertTrue(SetupToken.matches(SetupToken.value))
        assertTrue(SetupToken.matches(" ${SetupToken.value}\n"), "pasted with whitespace")
        assertFalse(SetupToken.matches(null))
        assertFalse(SetupToken.matches(""))
        assertFalse(SetupToken.matches(SetupToken.value.dropLast(1)))
    }

    @Test
    fun `wrong passwords from one address do not lock the account for another address`() = testApplication {
        application { module() }
        val admin = createAuthClient()
        assertEquals(HttpStatusCode.OK, admin.login("admin", "testpassword123").status)
        val user = "lock-${System.nanoTime()}"
        admin.createUser(user, "Right-password-1", "viewer")

        val config = ConfigLoader.load()
        val log = LoggerFactory.getLogger("test")
        repeat(5) { assertFalse(verifyCredentials(user, "wrong", "203.0.113.7", config, log)) }

        assertFalse(verifyCredentials(user, "Right-password-1", "203.0.113.7", config, log), "the guessing address is locked out")
        assertTrue(verifyCredentials(user, "Right-password-1", "203.0.113.8", config, log), "the owner's address is not")
    }

    @Test
    fun `logout and password change end the user's API tokens too`() = testApplication {
        application { module() }
        val admin = createAuthClient()
        assertEquals(HttpStatusCode.OK, admin.login("admin", "testpassword123").status)
        val user = "tok-${System.nanoTime()}"
        admin.createUser(user, "First-password-1", "viewer")

        suspend fun tokens(password: String): Pair<String, String> {
            val body = client.post("/api/token") {
                contentType(ContentType.Application.Json)
                setBody("""{"username":"$user","password":"$password"}""")
            }.bodyAsText()
            return field(body, "accessToken") to field(body, "refreshToken")
        }
        suspend fun status(accessToken: String) = client.get("/api/projects") { bearerAuth(accessToken) }.status
        suspend fun refresh(refreshToken: String) = client.post("/api/token/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$refreshToken"}""")
        }.status

        // Logout from the dashboard
        val (access1, refresh1) = tokens("First-password-1")
        assertEquals(HttpStatusCode.OK, status(access1))
        delay(1100) // `iat` has whole seconds
        val session = createAuthClient()
        assertEquals(HttpStatusCode.OK, session.login(user, "First-password-1").status)
        assertEquals(HttpStatusCode.OK, session.post("/api/logout").status)
        assertEquals(HttpStatusCode.Unauthorized, status(access1), "access token after logout")
        assertEquals(HttpStatusCode.Unauthorized, refresh(refresh1), "refresh token after logout")

        // Password change with a token
        val (access2, _) = tokens("First-password-1")
        delay(1100)
        val change = client.post("/api/me/password") {
            bearerAuth(access2)
            contentType(ContentType.Application.Json)
            setBody("""{"currentPassword":"First-password-1","newPassword":"Second-password-2"}""")
        }
        assertEquals(HttpStatusCode.OK, change.status)
        assertEquals(HttpStatusCode.Unauthorized, status(access2), "access token after password change")

        // A token issued afterwards works
        assertEquals(HttpStatusCode.OK, status(tokens("Second-password-2").first))
    }

    private fun payload(type: String = "pageview", eventName: String? = null, targetUrl: String? = null, properties: String? = null) =
        PageViewPayload(path = "/", sessionId = "s1", type = type, eventName = eventName, targetUrl = targetUrl, properties = properties)

    @Test
    fun `collect validation rejects markup in target URLs and properties that are not a JSON object`() {
        fun valid(p: PageViewPayload) = InputValidator.validatePageViewPayload(p).isValid

        assertTrue(valid(payload("outbound", "example.org", targetUrl = "https://example.org/a?b=c#d")))
        assertFalse(valid(payload("outbound", "example.org", targetUrl = "javascript:alert(1)")))
        assertFalse(valid(payload("outbound", "example.org", targetUrl = "https://example.org/\"><img src=x>")))
        assertFalse(valid(payload("download", "a.pdf", targetUrl = "/a.pdf")))

        assertTrue(valid(payload("custom", "signup", properties = """{"plan":"pro","revenue":9.5}""")))
        assertFalse(valid(payload("custom", "signup", properties = "<script>alert(1)</script>")))
        assertFalse(valid(payload("custom", "signup", properties = "[1,2]")))
        assertFalse(valid(payload("custom", "signup", properties = "\"text\"")))
    }

    @Test
    fun `a crawler's page view is accepted but not stored`() = testApplication {
        application { module() }
        val admin = createAuthClient()
        assertEquals(HttpStatusCode.OK, admin.login("admin", "testpassword123").status)
        val created = admin.post("/admin/projects") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"bots-${System.nanoTime()}","domain":"example.com"}""")
        }.bodyAsText()
        val key = field(created, "apiKey")
        val id = field(created, "id")

        suspend fun visit(userAgent: String) = client.post("/collect?key=$key") {
            header(HttpHeaders.UserAgent, userAgent)
            setBody("""{"path":"/","sessionId":"s1","type":"pageview"}""")
        }.status
        suspend fun stored() = Regex("\"total\":(\\d+)")
            .find(admin.get("/admin/projects/$id/events").bodyAsText())!!.groupValues[1].toInt()

        assertEquals(HttpStatusCode.Accepted, visit("Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"))
        assertEquals(0, stored())
        val safari = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) " +
            "Version/17.0 Safari/605.1.15"
        assertEquals(HttpStatusCode.Accepted, visit(safari))
        assertEquals(1, stored())
    }
}
