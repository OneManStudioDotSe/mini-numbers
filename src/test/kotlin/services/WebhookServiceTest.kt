package se.onemanstudio.services

import org.junit.Test
import kotlin.test.*

/**
 * Unit tests for WebhookService
 * Tests HMAC-SHA256 signing and payload handling
 */
class WebhookServiceTest {

    @Test
    fun `signPayload produces valid HMAC-SHA256 signature`() {
        val payload = """{"event":"test","projectId":"123"}"""
        val secret = "test-secret-key-abc123"

        val signature = WebhookService.signPayload(payload, secret)

        assertNotNull(signature)
        assertTrue(signature.length == 64, "SHA256 hex should be 64 chars, got ${signature.length}")
        assertTrue(signature.all { it in '0'..'9' || it in 'a'..'f' }, "Signature should be lowercase hex")
    }

    @Test
    fun `signPayload is deterministic for same inputs`() {
        val payload = """{"event":"goal_conversion","goalName":"Signup"}"""
        val secret = "deterministic-test-secret"

        val sig1 = WebhookService.signPayload(payload, secret)
        val sig2 = WebhookService.signPayload(payload, secret)

        assertEquals(sig1, sig2, "Same payload and secret should produce same signature")
    }

    @Test
    fun `signPayload produces different signatures for different payloads`() {
        val secret = "shared-secret"
        val payload1 = """{"event":"test1"}"""
        val payload2 = """{"event":"test2"}"""

        val sig1 = WebhookService.signPayload(payload1, secret)
        val sig2 = WebhookService.signPayload(payload2, secret)

        assertNotEquals(sig1, sig2, "Different payloads should produce different signatures")
    }

    @Test
    fun `signPayload produces different signatures for different secrets`() {
        val payload = """{"event":"test"}"""
        val secret1 = "secret-one"
        val secret2 = "secret-two"

        val sig1 = WebhookService.signPayload(payload, secret1)
        val sig2 = WebhookService.signPayload(payload, secret2)

        assertNotEquals(sig1, sig2, "Different secrets should produce different signatures")
    }

    @Test
    fun `signPayload handles empty payload`() {
        val signature = WebhookService.signPayload("", "secret")

        assertNotNull(signature)
        assertEquals(64, signature.length)
    }

    @Test
    fun `signPayload handles unicode payload`() {
        val payload = """{"event":"test","data":"日本語テスト"}"""
        val secret = "unicode-secret"

        val signature = WebhookService.signPayload(payload, secret)

        assertNotNull(signature)
        assertEquals(64, signature.length)
    }

    @Test
    fun `signPayload handles large payload`() {
        val payload = """{"data":"${"x".repeat(10000)}"}"""
        val secret = "large-payload-secret"

        val signature = WebhookService.signPayload(payload, secret)

        assertNotNull(signature)
        assertEquals(64, signature.length)
    }
}
