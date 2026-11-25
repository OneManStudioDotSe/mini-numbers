package se.onemanstudio.mininumbers.sample

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildPayloadTest {
    private val session = "0123456789abcdef0123456789abcdef"

    private fun build(
        type: String,
        path: String = "/shop",
        eventName: String? = null,
        properties: Map<String, Any> = emptyMap(),
        targetUrl: String? = null,
        key: String = "key",
        optOut: Boolean = false,
    ) = buildPayload(type, path, session, eventName, properties, targetUrl, key, optOut)

    @Test fun pageviewHasNoEventNameOrReferrer() {
        val body = build("pageview", eventName = "ignored")!!
        assertEquals("pageview", body.getString("type"))
        assertEquals("/shop", body.getString("path"))
        assertEquals(session, body.getString("sessionId"))
        assertFalse(body.has("eventName"))
        assertFalse(body.has("referrer"))
        assertFalse(body.has("properties"))
    }

    @Test fun heartbeatHasNoEventName() {
        assertFalse(build("heartbeat", eventName = "x")!!.has("eventName"))
    }

    @Test fun propertiesIsAStringHoldingJson() {
        val body = build("custom", eventName = "signup", properties = mapOf("plan" to "free", "source" to "sample-app"))!!
        val properties = body.get("properties")
        assertTrue(properties is String)
        assertEquals("free", JSONObject(properties as String).getString("plan"))
        assertEquals("signup", body.getString("eventName"))
    }

    @Test fun purchaseCarriesRevenueAndCurrency() {
        val body = build("custom", eventName = "purchase", properties = mapOf("revenue" to 12.0, "currency" to "EUR"))!!
        val properties = JSONObject(body.getString("properties"))
        assertEquals(12.0, properties.getDouble("revenue"), 0.0)
        assertEquals("EUR", properties.getString("currency"))
    }

    @Test fun refundRevenueIsNegative() {
        val body = build("custom", eventName = "refund", properties = mapOf("revenue" to -8.5, "currency" to "EUR"))!!
        assertEquals(-8.5, JSONObject(body.getString("properties")).getDouble("revenue"), 0.0)
    }

    @Test fun outboundHasHostAndTargetUrl() {
        val url = "https://github.com/OneManStudioDotSe/mini-numbers"
        val body = build("outbound", eventName = "github.com", targetUrl = url)!!
        assertEquals("github.com", body.getString("eventName"))
        assertEquals(url, body.getString("targetUrl"))
    }

    @Test fun nothingBuiltWhenOptedOut() = assertNull(build("pageview", optOut = true))

    @Test fun nothingBuiltWithoutKey() {
        assertNull(build("pageview", key = ""))
        assertNull(build("pageview", key = "  "))
    }

    @Test fun builtWhenAllowed() = assertNotNull(build("pageview"))
}
