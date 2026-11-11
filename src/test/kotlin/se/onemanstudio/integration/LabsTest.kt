package se.onemanstudio.integration

import io.ktor.client.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import org.junit.Test
import org.slf4j.LoggerFactory
import se.onemanstudio.core.Lab
import se.onemanstudio.core.Labs
import se.onemanstudio.core.ServiceManager
import se.onemanstudio.module
import kotlin.test.assertEquals

/** Labs are off unless named in LABS: their routes answer 404 and /admin/labs lists only the ones switched on. */
class LabsTest {

    private val logger = LoggerFactory.getLogger(LabsTest::class.java)

    /** The build switches every lab on for the suite; run [block] with LABS=[value] on freshly started services. */
    private fun withLabs(value: String, block: () -> Unit) {
        val previous = System.getProperty("LABS")
        System.setProperty("LABS", value)
        ServiceManager.shutdown(logger)
        try {
            block()
        } finally {
            if (previous == null) System.clearProperty("LABS") else System.setProperty("LABS", previous)
            ServiceManager.shutdown(logger)
        }
    }

    private fun ApplicationTestBuilder.adminClient(): HttpClient = createClient { install(HttpCookies) }

    private suspend fun HttpClient.loginAndCreateProject(): Pair<String, String> {
        assertEquals(HttpStatusCode.OK, post("/api/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"testpassword123"}""")
        }.status)
        val name = "labs-${System.nanoTime()}"
        val created = post("/admin/projects") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"$name","domain":"example.com"}""")
        }
        val body = Json.parseToJsonElement(created.bodyAsText()).jsonObject
        return body["id"]!!.jsonPrimitive.content to body["apiKey"]!!.jsonPrimitive.content
    }

    private suspend fun HttpClient.enabledLabs(): List<String> =
        Json.parseToJsonElement(get("/admin/labs").bodyAsText()).jsonObject["enabled"]!!.jsonArray.map { it.jsonPrimitive.content }

    @Test
    fun `LABS is parsed case-insensitively and unknown names are ignored`() {
        assertEquals(setOf(Lab.WEBHOOKS, Lab.FUNNELS), Labs.parse(" Webhooks, funnels,bogus,,"))
        assertEquals(setOf(Lab.EMAIL_REPORTS), Labs.parse("email-reports"))
        assertEquals(emptySet(), Labs.parse(null))
        assertEquals(emptySet(), Labs.parse(""))
    }

    @Test
    fun `every lab is off by default and its routes answer 404`() = withLabs("") {
        testApplication {
            application { module() }
            val admin = adminClient()
            val (id, key) = admin.loginAndCreateProject()

            assertEquals(emptyList(), admin.enabledLabs())
            assertEquals(HttpStatusCode.NotFound, client.get("/widget/realtime?key=$key").status, "widgets")
            assertEquals(HttpStatusCode.NotFound, client.get("/widget/toppages?key=$key").status, "widgets")
            for (path in listOf("funnels", "segments", "webhooks", "email-reports", "revenue")) {
                assertEquals(HttpStatusCode.NotFound, admin.get("/admin/projects/$id/$path").status, path)
            }
            assertEquals(HttpStatusCode.NotFound, admin.get("/admin/smtp/status").status, "smtp status")
            // Goals are not a lab
            assertEquals(HttpStatusCode.OK, admin.get("/admin/projects/$id/goals").status, "goals")
        }
    }

    @Test
    fun `a lab named in LABS is mounted and the others stay off`() = withLabs("funnels") {
        testApplication {
            application { module() }
            val admin = adminClient()
            val (id, key) = admin.loginAndCreateProject()

            assertEquals(listOf("funnels"), admin.enabledLabs())
            assertEquals(HttpStatusCode.OK, admin.get("/admin/projects/$id/funnels").status, "funnels")
            assertEquals(HttpStatusCode.NotFound, admin.get("/admin/projects/$id/webhooks").status, "webhooks")
            assertEquals(HttpStatusCode.NotFound, client.get("/widget/realtime?key=$key").status, "widgets")
        }
    }
}
