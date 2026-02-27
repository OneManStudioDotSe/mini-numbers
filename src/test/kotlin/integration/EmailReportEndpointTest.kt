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
        if (authClient.login().status != HttpStatusCode.OK) return@testApplication

        val projectId = authClient.createTestProject("Email Test 1", "email-test1.com")
            ?: return@testApplication

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
        if (authClient.login().status != HttpStatusCode.OK) return@testApplication

        val projectId = authClient.createTestProject("Email Test 2", "email-test2.com")
            ?: return@testApplication

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
        if (authClient.login().status != HttpStatusCode.OK) return@testApplication

        val projectId = authClient.createTestProject("Email Test 3", "email-test3.com")
            ?: return@testApplication

        val response = authClient.post("/admin/projects/$projectId/email-reports") {
            contentType(ContentType.Application.Json)
            setBody("""{"recipientEmail":"test@example.com","schedule":"BIWEEKLY"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("Schedule"))
    }

    @Test
    fun `delete nonexistent email report returns 404`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        if (authClient.login().status != HttpStatusCode.OK) return@testApplication

        val projectId = authClient.createTestProject("Email Test 4", "email-test4.com")
            ?: return@testApplication

        val response = authClient.delete("/admin/projects/$projectId/email-reports/00000000-0000-0000-0000-000000000000")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `email report endpoint with invalid project ID returns 400`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        if (authClient.login().status != HttpStatusCode.OK) return@testApplication

        val response = authClient.get("/admin/projects/not-a-uuid/email-reports")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `smtp status endpoint returns response`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        if (authClient.login().status != HttpStatusCode.OK) return@testApplication

        val response = authClient.get("/admin/smtp/status")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("configured"))
    }

    @Test
    fun `create and list email reports`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        if (authClient.login().status != HttpStatusCode.OK) return@testApplication

        val projectId = authClient.createTestProject("Email Test 5", "email-test5.com")
            ?: return@testApplication

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
    fun `update nonexistent email report returns 404`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        if (authClient.login().status != HttpStatusCode.OK) return@testApplication

        val projectId = authClient.createTestProject("Email Test 6", "email-test6.com")
            ?: return@testApplication

        val response = authClient.put("/admin/projects/$projectId/email-reports/00000000-0000-0000-0000-000000000000") {
            contentType(ContentType.Application.Json)
            setBody("""{"isActive":false}""")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
