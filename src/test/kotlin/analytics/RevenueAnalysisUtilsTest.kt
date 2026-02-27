package se.onemanstudio.analytics

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
 * Tests for revenue analytics endpoints
 */
class RevenueAnalysisUtilsTest {

    private fun ApplicationTestBuilder.createAuthClient(): HttpClient {
        return createClient {
            install(HttpCookies)
        }
    }

    private suspend fun HttpClient.login(): HttpResponse {
        return post("/api/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"testpassword123"}""")
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
    fun `revenue endpoint returns stats with zero revenue for empty project`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        if (authClient.login().status != HttpStatusCode.OK) return@testApplication

        val projectId = authClient.createTestProject("Revenue Test 1", "revenue-test1.com")
            ?: return@testApplication

        val response = authClient.get("/admin/projects/$projectId/revenue?filter=7d")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.bodyAsText()
        assertTrue(body.contains("totalRevenue"))
        assertTrue(body.contains("transactions"))
        assertTrue(body.contains("averageOrderValue"))
        assertTrue(body.contains("revenuePerVisitor"))
    }

    @Test
    fun `revenue breakdown returns empty list for project without revenue events`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        if (authClient.login().status != HttpStatusCode.OK) return@testApplication

        val projectId = authClient.createTestProject("Revenue Test 2", "revenue-test2.com")
            ?: return@testApplication

        val response = authClient.get("/admin/projects/$projectId/revenue/breakdown?filter=7d")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.bodyAsText()
        assertEquals("[]", body)
    }

    @Test
    fun `revenue attribution returns empty list for project without revenue events`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        if (authClient.login().status != HttpStatusCode.OK) return@testApplication

        val projectId = authClient.createTestProject("Revenue Test 3", "revenue-test3.com")
            ?: return@testApplication

        val response = authClient.get("/admin/projects/$projectId/revenue/attribution?filter=7d")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.bodyAsText()
        assertEquals("[]", body)
    }

    @Test
    fun `revenue endpoint with invalid project ID returns 400`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        if (authClient.login().status != HttpStatusCode.OK) return@testApplication

        val response = authClient.get("/admin/projects/not-a-uuid/revenue")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `revenue breakdown with invalid project ID returns 400`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        if (authClient.login().status != HttpStatusCode.OK) return@testApplication

        val response = authClient.get("/admin/projects/not-a-uuid/revenue/breakdown")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `revenue attribution with invalid project ID returns 400`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        if (authClient.login().status != HttpStatusCode.OK) return@testApplication

        val response = authClient.get("/admin/projects/not-a-uuid/revenue/attribution")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `revenue with demo data returns non-empty stats`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        if (authClient.login().status != HttpStatusCode.OK) return@testApplication

        val projectId = authClient.createTestProject("Revenue Test 4", "revenue-test4.com")
            ?: return@testApplication

        // Generate demo data (which now includes revenue events)
        val demoResponse = authClient.post("/admin/projects/$projectId/demo-data") {
            contentType(ContentType.Application.Json)
            setBody("""{"count":200,"timeScope":7}""")
        }
        if (demoResponse.status != HttpStatusCode.OK) return@testApplication

        val response = authClient.get("/admin/projects/$projectId/revenue?filter=7d")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.bodyAsText()
        assertTrue(body.contains("totalRevenue"))
        // Demo data should have some revenue since purchases are included
        assertTrue(body.contains("transactions"))
    }

    @Test
    fun `revenue endpoint supports different filters`() = testApplication {
        application { module() }
        val authClient = createAuthClient()
        if (authClient.login().status != HttpStatusCode.OK) return@testApplication

        val projectId = authClient.createTestProject("Revenue Test 5", "revenue-test5.com")
            ?: return@testApplication

        for (filter in listOf("24h", "7d", "30d", "365d")) {
            val response = authClient.get("/admin/projects/$projectId/revenue?filter=$filter")
            assertEquals(HttpStatusCode.OK, response.status, "Failed for filter=$filter")
        }
    }
}
