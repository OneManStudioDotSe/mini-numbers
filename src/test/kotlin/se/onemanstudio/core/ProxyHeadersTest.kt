package se.onemanstudio.core

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import org.junit.Test
import kotlin.test.*

/**
 * The visitor IP (`request.origin.remoteHost`) feeds the visitor hash, GeoIP and rate limiting.
 * Behind a proxy it must come from X-Forwarded-For, but only when the operator says a proxy is there.
 */
class ProxyHeadersTest {

    private fun ApplicationTestBuilder.echoClientIp(trustProxy: Boolean) {
        application {
            configureProxyHeaders(trustProxy)
            routing {
                get("/ip") { call.respondText(call.request.origin.remoteHost) }
            }
        }
    }

    @Test
    fun `with TRUST_PROXY the visitor IP is the address the proxy added`() = testApplication {
        echoClientIp(trustProxy = true)

        // A visitor can prepend a fake address; the proxy appends the real one last
        val ip = client.get("/ip") { header("X-Forwarded-For", "6.6.6.6, 203.0.113.7") }.bodyAsText()

        assertEquals("203.0.113.7", ip)
    }

    @Test
    fun `without TRUST_PROXY the X-Forwarded-For header is ignored`() = testApplication {
        echoClientIp(trustProxy = false)

        val ip = client.get("/ip") { header("X-Forwarded-For", "203.0.113.7") }.bodyAsText()

        assertNotEquals("203.0.113.7", ip)
    }
}
