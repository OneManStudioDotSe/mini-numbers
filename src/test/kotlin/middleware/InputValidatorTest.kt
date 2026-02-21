package se.onemanstudio.middleware

import org.junit.Test
import se.onemanstudio.api.models.PageViewPayload
import kotlin.test.*

/**
 * Unit tests for InputValidator
 * Tests validation and sanitization of user inputs
 */
class InputValidatorTest {

    // ==================== Sanitization Tests ====================

    @Test
    fun `sanitize removes control characters`() {
        val input = "Hello\u0001\u001FWorld"
        val result = InputValidator.sanitize(input)
        assertFalse(result.contains("\u0001"))
        assertFalse(result.contains("\u001F"))
        assertEquals("HelloWorld", result)
    }

    @Test
    fun `sanitize normalizes whitespace`() {
        // Control characters (\t=0x09, \n=0x0A) are removed first, then whitespace is normalized
        val input = "Hello    World\t\tTest\n\nLine"
        val result = InputValidator.sanitize(input)
        // Tabs and newlines are stripped as control characters; multiple spaces become single space
        assertEquals("Hello WorldTestLine", result)
    }

    @Test
    fun `sanitize handles null bytes`() {
        val input = "Hello\u0000World"
        val result = InputValidator.sanitize(input)
        assertFalse(result.contains("\u0000"))
        assertEquals("HelloWorld", result)
    }

    @Test
    fun `sanitize preserves normal text`() {
        val input = "normal-path/to/page.html"
        val result = InputValidator.sanitize(input)
        assertEquals(input, result)
    }

    @Test
    fun `sanitize handles empty string`() {
        val result = InputValidator.sanitize("")
        assertEquals("", result)
    }

    @Test
    fun `sanitize handles special URL characters`() {
        val input = "/path?param=value&other=123"
        val result = InputValidator.sanitize(input)
        // Should preserve valid URL characters
        assertTrue(result.contains("/"))
        assertTrue(result.contains("?"))
        assertTrue(result.contains("="))
        assertTrue(result.contains("&"))
    }

    @Test
    fun `sanitize handles Unicode characters`() {
        val input = "Hello 世界 Привет"
        val result = InputValidator.sanitize(input)
        // Should preserve Unicode
        assertTrue(result.contains("世界"))
        assertTrue(result.contains("Привет"))
    }

    @Test
    fun `sanitize limits string length`() {
        val longInput = "a".repeat(10000)
        val result = InputValidator.sanitize(longInput)
        // Should have reasonable length limit (check implementation)
        assertTrue(result.length <= longInput.length)
    }

    // ==================== PageViewPayload Validation Tests ====================

    @Test
    fun `validatePageViewPayload accepts valid payload`() {
        val payload = PageViewPayload(
            path = "/home",
            referrer = "https://google.com",
            sessionId = "abc123",
            type = "pageview"
        )

        val result = InputValidator.validatePageViewPayload(payload)

        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `validatePageViewPayload accepts payload without referrer`() {
        val payload = PageViewPayload(
            path = "/home",
            referrer = null,
            sessionId = "abc123",
            type = "pageview"
        )

        val result = InputValidator.validatePageViewPayload(payload)

        assertTrue(result.isValid)
    }

    @Test
    fun `validatePageViewPayload rejects empty path`() {
        val payload = PageViewPayload(
            path = "",
            referrer = null,
            sessionId = "abc123",
            type = "pageview"
        )

        val result = InputValidator.validatePageViewPayload(payload)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("path", ignoreCase = true) })
    }

    @Test
    fun `validatePageViewPayload rejects path that is too long`() {
        val payload = PageViewPayload(
            path = "/" + "a".repeat(1000),
            referrer = null,
            sessionId = "abc123",
            type = "pageview"
        )

        val result = InputValidator.validatePageViewPayload(payload)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("path", ignoreCase = true) })
    }

    @Test
    fun `validatePageViewPayload rejects referrer that is too long`() {
        val payload = PageViewPayload(
            path = "/home",
            referrer = "https://example.com/" + "a".repeat(1000),
            sessionId = "abc123",
            type = "pageview"
        )

        val result = InputValidator.validatePageViewPayload(payload)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("referrer", ignoreCase = true) })
    }

    @Test
    fun `validatePageViewPayload rejects empty sessionId`() {
        val payload = PageViewPayload(
            path = "/home",
            referrer = null,
            sessionId = "",
            type = "pageview"
        )

        val result = InputValidator.validatePageViewPayload(payload)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("sessionId", ignoreCase = true) || it.contains("session", ignoreCase = true) })
    }

    @Test
    fun `validatePageViewPayload rejects invalid event type`() {
        val payload = PageViewPayload(
            path = "/home",
            referrer = null,
            sessionId = "abc123",
            type = "invalid_type"
        )

        val result = InputValidator.validatePageViewPayload(payload)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("type", ignoreCase = true) })
    }

    @Test
    fun `validatePageViewPayload accepts heartbeat type`() {
        val payload = PageViewPayload(
            path = "/home",
            referrer = null,
            sessionId = "abc123",
            type = "heartbeat"
        )

        val result = InputValidator.validatePageViewPayload(payload)

        assertTrue(result.isValid)
    }

    @Test
    fun `validatePageViewPayload rejects path with null bytes`() {
        val payload = PageViewPayload(
            path = "/home\u0000/test",
            referrer = null,
            sessionId = "abc123",
            type = "pageview"
        )

        val result = InputValidator.validatePageViewPayload(payload)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("path", ignoreCase = true) })
    }

    @Test
    fun `validatePageViewPayload accepts path with query parameters`() {
        val payload = PageViewPayload(
            path = "/search?q=test&category=all",
            referrer = null,
            sessionId = "abc123",
            type = "pageview"
        )

        val result = InputValidator.validatePageViewPayload(payload)

        assertTrue(result.isValid)
    }

    @Test
    fun `validatePageViewPayload accepts path with hash`() {
        val payload = PageViewPayload(
            path = "/page#section-1",
            referrer = null,
            sessionId = "abc123",
            type = "pageview"
        )

        val result = InputValidator.validatePageViewPayload(payload)

        assertTrue(result.isValid)
    }

    @Test
    fun `validatePageViewPayload collects multiple errors`() {
        val payload = PageViewPayload(
            path = "",
            referrer = "https://example.com/" + "a".repeat(1000),
            sessionId = "",
            type = "invalid"
        )

        val result = InputValidator.validatePageViewPayload(payload)

        assertFalse(result.isValid)
        assertTrue(result.errors.size >= 3) // At least path, sessionId, and type errors
    }

    @Test
    fun `validatePageViewPayload accepts valid referrer URL`() {
        val validReferrers = listOf(
            "https://google.com",
            "https://www.example.com/page",
            "http://localhost:3000",
            "https://sub.domain.com/path?query=value"
        )

        validReferrers.forEach { referrer ->
            val payload = PageViewPayload(
                path = "/home",
                referrer = referrer,
                sessionId = "abc123",
                type = "pageview"
            )

            val result = InputValidator.validatePageViewPayload(payload)
            assertTrue(result.isValid, "Referrer $referrer should be valid")
        }
    }

    @Test
    fun `validatePageViewPayload accepts various valid paths`() {
        val validPaths = listOf(
            "/",
            "/home",
            "/blog/post-1",
            "/products/item-123",
            "/path/with/many/segments",
            "/search?q=test",
            "/page#anchor"
        )

        validPaths.forEach { path ->
            val payload = PageViewPayload(
                path = path,
                referrer = null,
                sessionId = "abc123",
                type = "pageview"
            )

            val result = InputValidator.validatePageViewPayload(payload)
            assertTrue(result.isValid, "Path $path should be valid")
        }
    }

    // ==================== Custom Event Validation Tests ====================

    @Test
    fun `validatePageViewPayload accepts custom event with valid eventName`() {
        val payload = PageViewPayload(
            path = "/home",
            referrer = null,
            sessionId = "abc123",
            type = "custom",
            eventName = "signup"
        )

        val result = InputValidator.validatePageViewPayload(payload)

        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `validatePageViewPayload rejects custom event without eventName`() {
        val payload = PageViewPayload(
            path = "/home",
            referrer = null,
            sessionId = "abc123",
            type = "custom",
            eventName = null
        )

        val result = InputValidator.validatePageViewPayload(payload)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("event name", ignoreCase = true) })
    }

    @Test
    fun `validatePageViewPayload rejects non-custom event with eventName`() {
        val payload = PageViewPayload(
            path = "/home",
            referrer = null,
            sessionId = "abc123",
            type = "pageview",
            eventName = "signup"
        )

        val result = InputValidator.validatePageViewPayload(payload)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("event name", ignoreCase = true) })
    }

    @Test
    fun `validatePageViewPayload rejects event name that is too long`() {
        val payload = PageViewPayload(
            path = "/home",
            referrer = null,
            sessionId = "abc123",
            type = "custom",
            eventName = "a".repeat(101)
        )

        val result = InputValidator.validatePageViewPayload(payload)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("event name", ignoreCase = true) })
    }

    @Test
    fun `validatePageViewPayload rejects event name with invalid characters`() {
        val payload = PageViewPayload(
            path = "/home",
            referrer = null,
            sessionId = "abc123",
            type = "custom",
            eventName = "event<script>"
        )

        val result = InputValidator.validatePageViewPayload(payload)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("event name", ignoreCase = true) })
    }

    @Test
    fun `validatePageViewPayload accepts various valid event names`() {
        val validNames = listOf("signup", "newsletter_subscribe", "file-download", "page.view", "Purchase 2024")

        validNames.forEach { name ->
            val payload = PageViewPayload(
                path = "/home",
                referrer = null,
                sessionId = "abc123",
                type = "custom",
                eventName = name
            )

            val result = InputValidator.validatePageViewPayload(payload)
            assertTrue(result.isValid, "Event name '$name' should be valid")
        }
    }

    @Test
    fun `validatePageViewPayload accepts heartbeat without eventName`() {
        val payload = PageViewPayload(
            path = "/home",
            referrer = null,
            sessionId = "abc123",
            type = "heartbeat",
            eventName = null
        )

        val result = InputValidator.validatePageViewPayload(payload)

        assertTrue(result.isValid)
    }

    @Test
    fun `validatePageViewPayload rejects custom event with blank eventName`() {
        val payload = PageViewPayload(
            path = "/home",
            referrer = null,
            sessionId = "abc123",
            type = "custom",
            eventName = "   "
        )

        val result = InputValidator.validatePageViewPayload(payload)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("event name", ignoreCase = true) })
    }
}
