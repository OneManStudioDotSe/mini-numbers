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
 * Integration tests for email report API endpoints
 * Tests CRUD operations, validation, and SMTP status
 */
class EmailReportEndpointTest {

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

    private suspend fun HttpClient.createTestProject(name: String, domain: String): String? {
        val response = post("/admin/projects") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"$name","domain":"$domain"}""")
        }
        if (response.status != HttpStatusCode.Created) return null
        return Regex(""""id"\s*:\s*"([^"]+)"""").find(response.bodyAsText())?.groupValues?.get(1)
    }

    @Test
    fun `create email report with invalid email returns 400`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        assertEquals(HttpStatusCode.OK, authClient.login().status, "login")

        val projectId = assertNotNull(authClient.createTestProject("Email Test 1", "email-test1.com"), "projectId")

        val response = authClient.post("/admin/projects/$projectId/email-reports") {
            contentType(ContentType.Application.Json)
            setBody("""{"recipientEmail":"not-an-email","schedule":"WEEKLY"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("email", ignoreCase = true))
    }

    @Test
    fun `create email report with blank email returns 400`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        assertEquals(HttpStatusCode.OK, authClient.login().status, "login")

        val projectId = assertNotNull(authClient.createTestProject("Email Test 2", "email-test2.com"), "projectId")

        val response = authClient.post("/admin/projects/$projectId/email-reports") {
            contentType(ContentType.Application.Json)
            setBody("""{"recipientEmail":"","schedule":"WEEKLY"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `create email report with invalid schedule returns 400`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        assertEquals(HttpStatusCode.OK, authClient.login().status, "login")

        val projectId = assertNotNull(authClient.createTestProject("Email Test 3", "email-test3.com"), "projectId")

        val response = authClient.post("/admin/projects/$projectId/email-reports") {
            contentType(ContentType.Application.Json)
            setBody("""{"recipientEmail":"test@example.com","schedule":"BIWEEKLY"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("Schedule"))
    }

    @Test
    fun `create email report with unknown timezone returns 400`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        assertEquals(HttpStatusCode.OK, authClient.login().status, "login")
        val projectId = assertNotNull(authClient.createTestProject("TZ Test", "tz-test.com"), "projectId")

        val response = authClient.post("/admin/projects/$projectId/email-reports") {
            contentType(ContentType.Application.Json)
            setBody("""{"recipientEmail":"owner@example.com","schedule":"WEEKLY","timezone":"Mars/Olympus"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `delete nonexistent email report returns 404`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        assertEquals(HttpStatusCode.OK, authClient.login().status, "login")

        val projectId = assertNotNull(authClient.createTestProject("Email Test 4", "email-test4.com"), "projectId")

        val response = authClient.delete("/admin/projects/$projectId/email-reports/00000000-0000-0000-0000-000000000000")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `email report endpoint with invalid project ID returns 400`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        assertEquals(HttpStatusCode.OK, authClient.login().status, "login")

        val response = authClient.get("/admin/projects/not-a-uuid/email-reports")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `smtp status endpoint returns response`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        assertEquals(HttpStatusCode.OK, authClient.login().status, "login")

        val response = authClient.get("/admin/smtp/status")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("configured"))
    }

    @Test
    fun `create and list email reports`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        assertEquals(HttpStatusCode.OK, authClient.login().status, "login")

        val projectId = assertNotNull(authClient.createTestProject("Email Test 5", "email-test5.com"), "projectId")

        // Create a report
        val createResponse = authClient.post("/admin/projects/$projectId/email-reports") {
            contentType(ContentType.Application.Json)
            setBody("""{"recipientEmail":"test@example.com","schedule":"WEEKLY","sendHour":9}""")
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)

        // List reports
        val listResponse = authClient.get("/admin/projects/$projectId/email-reports")
        assertEquals(HttpStatusCode.OK, listResponse.status)
        val body = listResponse.bodyAsText()
        assertTrue(body.contains("test@example.com"))
        assertTrue(body.contains("WEEKLY"))
    }

    @Test
    fun `a report takes exactly one valid address, and an update cannot set an unknown schedule`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        assertEquals(HttpStatusCode.OK, authClient.login().status, "login")
        val projectId = assertNotNull(authClient.createTestProject("Email Test 7", "email-test7.com"), "projectId")

        suspend fun create(recipient: String) = authClient.post("/admin/projects/$projectId/email-reports") {
            contentType(ContentType.Application.Json)
            setBody("""{"recipientEmail":"$recipient"}""")
        }
        for (bad in listOf("@", "ann@", "@example.com", "ann@example.com,bob@example.com", "Ann <ann@example.com>",
            "ann example@example.com", "a".repeat(320) + "@example.com")) {
            assertEquals(HttpStatusCode.BadRequest, create(bad).status, bad)
        }
        assertEquals("[]", authClient.get("/admin/projects/$projectId/email-reports").bodyAsText(), "nothing was stored")

        val created = create("ann.o'neil+reports@mail.example.com")
        assertEquals(HttpStatusCode.Created, created.status)
        val reportId = assertNotNull(Regex(""""id"\s*:\s*"([^"]+)"""").find(created.bodyAsText())?.groupValues?.get(1))

        suspend fun update(body: String) = authClient.put("/admin/projects/$projectId/email-reports/$reportId") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.status
        assertEquals(HttpStatusCode.BadRequest, update("""{"schedule":"HOURLY"}"""))
        assertEquals(HttpStatusCode.OK, update("""{"schedule":"daily"}"""))
        val listed = authClient.get("/admin/projects/$projectId/email-reports").bodyAsText()
        assertTrue(listed.contains(""""schedule":"DAILY"""") && !listed.contains("HOURLY"), listed)
    }

    @Test
    fun `update nonexistent email report returns 404`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        assertEquals(HttpStatusCode.OK, authClient.login().status, "login")

        val projectId = assertNotNull(authClient.createTestProject("Email Test 6", "email-test6.com"), "projectId")

        val response = authClient.put("/admin/projects/$projectId/email-reports/00000000-0000-0000-0000-000000000000") {
            contentType(ContentType.Application.Json)
            setBody("""{"isActive":false}""")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
