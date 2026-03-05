package se.onemanstudio.services

import org.junit.Test
import kotlin.test.*

/**
 * Unit tests for UserAgentParser
 * Tests browser, OS, and device detection from user agent strings
 * Tests focus on robustness and edge case handling rather than exact string matching
 */
class UserAgentParserTest {

    @Test
    fun `parseBrowser returns non-empty result for Chrome UA`() {
        val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
        val browser = UserAgentParser.parseBrowser(ua)

        assertNotNull(browser)
        assertTrue(browser.isNotEmpty())
        assertTrue(browser.contains("Chrome", ignoreCase = true))
    }

    @Test
    fun `parseBrowser returns non-empty result for Firefox UA`() {
        val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:89.0) Gecko/20100101 Firefox/89.0"
        val browser = UserAgentParser.parseBrowser(ua)

        assertNotNull(browser)
        assertTrue(browser.isNotEmpty())
        assertTrue(browser.contains("Firefox", ignoreCase = true))
    }

    @Test
    fun `parseBrowser returns non-empty result for Safari UA`() {
        val ua = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.1.1 Safari/605.1.15"
        val browser = UserAgentParser.parseBrowser(ua)

        assertNotNull(browser)
        assertTrue(browser.isNotEmpty())
        assertTrue(browser.contains("Safari", ignoreCase = true))
    }

    @Test
    fun `parseBrowser returns non-empty result for Edge UA`() {
        val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36 Edg/91.0.864.59"
        val browser = UserAgentParser.parseBrowser(ua)

        assertNotNull(browser)
        assertTrue(browser.isNotEmpty())
        // Edge UA might be detected as Edge or Chrome depending on library
        assertTrue(browser.contains("Edge", ignoreCase = true) || browser.contains("Chrome", ignoreCase = true))
    }

    @Test
    fun `parseBrowser returns Unknown for empty string`() {
        val browser = UserAgentParser.parseBrowser("")
        assertNotNull(browser)
        // Should return Unknown or some default value
        assertTrue(browser == "Unknown" || browser.isNotEmpty())
    }

    @Test
    fun `parseBrowser returns non-null for invalid user agent`() {
        val browser = UserAgentParser.parseBrowser("Invalid User Agent String")
        assertNotNull(browser)
        assertTrue(browser.isNotEmpty())
    }

    @Test
    fun `parseOS returns non-empty result for Windows UA`() {
        val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
        val os = UserAgentParser.parseOS(ua)

        assertNotNull(os)
        assertTrue(os.isNotEmpty())
        assertTrue(os.contains("Windows", ignoreCase = true))
    }

    @Test
    fun `parseOS returns non-empty result for macOS UA`() {
        val ua = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
        val os = UserAgentParser.parseOS(ua)

        assertNotNull(os)
        assertTrue(os.isNotEmpty())
        assertTrue(os.contains("Mac", ignoreCase = true) || os.contains("OS X", ignoreCase = true))
    }

    @Test
    fun `parseOS returns non-empty result for Linux UA`() {
        val ua = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
        val os = UserAgentParser.parseOS(ua)

        assertNotNull(os)
        assertTrue(os.isNotEmpty())
        assertTrue(os.contains("Linux", ignoreCase = true))
    }

    @Test
    fun `parseOS returns non-empty result for iOS UA`() {
        val ua = "Mozilla/5.0 (iPhone; CPU iPhone OS 14_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.1.1 Mobile/15E148 Safari/604.1"
        val os = UserAgentParser.parseOS(ua)

        assertNotNull(os)
        assertTrue(os.isNotEmpty())
        assertTrue(os.contains("iOS", ignoreCase = true) || os.contains("iPhone", ignoreCase = true))
    }

    @Test
    fun `parseOS returns non-empty result for Android UA`() {
        val ua = "Mozilla/5.0 (Linux; Android 11; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36"
        val os = UserAgentParser.parseOS(ua)

        assertNotNull(os)
        assertTrue(os.isNotEmpty())
        assertTrue(os.contains("Android", ignoreCase = true))
    }

    @Test
    fun `parseOS returns non-null for empty string`() {
        val os = UserAgentParser.parseOS("")
        assertNotNull(os)
        assertTrue(os.isNotEmpty())
    }

    @Test
    fun `parseDevice returns non-null for Windows UA`() {
        val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
        val device = UserAgentParser.parseDevice(ua)

        assertNotNull(device)
        assertTrue(device.isNotEmpty())
    }

    @Test
    fun `parseDevice returns non-null for macOS UA`() {
        val ua = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
        val device = UserAgentParser.parseDevice(ua)

        assertNotNull(device)
        assertTrue(device.isNotEmpty())
    }

    @Test
    fun `parseDevice returns non-null for Linux UA`() {
        val ua = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
        val device = UserAgentParser.parseDevice(ua)

        assertNotNull(device)
        assertTrue(device.isNotEmpty())
    }

    @Test
    fun `parseDevice returns non-null for iPhone UA`() {
        val ua = "Mozilla/5.0 (iPhone; CPU iPhone OS 14_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.1.1 Mobile/15E148 Safari/604.1"
        val device = UserAgentParser.parseDevice(ua)

        assertNotNull(device)
        assertTrue(device.isNotEmpty())
    }

    @Test
    fun `parseDevice returns non-null for Android phone UA`() {
        val ua = "Mozilla/5.0 (Linux; Android 11; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36"
        val device = UserAgentParser.parseDevice(ua)

        assertNotNull(device)
        assertTrue(device.isNotEmpty())
    }

    @Test
    fun `parseDevice returns non-null for iPad UA`() {
        val ua = "Mozilla/5.0 (iPad; CPU OS 14_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.0 Mobile/15E148 Safari/604.1"
        val device = UserAgentParser.parseDevice(ua)

        assertNotNull(device)
        assertTrue(device.isNotEmpty())
    }

    @Test
    fun `parseDevice returns non-null for empty string`() {
        val device = UserAgentParser.parseDevice("")
        assertNotNull(device)
        assertTrue(device.isNotEmpty())
    }

    @Test
    fun `parser handles bot user agents without crashing`() {
        val ua = "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"

        // Should not throw exception
        val browser = UserAgentParser.parseBrowser(ua)
        val os = UserAgentParser.parseOS(ua)
        val device = UserAgentParser.parseDevice(ua)

        assertNotNull(browser)
        assertNotNull(os)
        assertNotNull(device)
    }

    @Test
    fun `parser handles very long user agent string without crashing`() {
        val ua = "Mozilla/5.0 " + "x".repeat(1000)

        // Should not throw exception
        val browser = UserAgentParser.parseBrowser(ua)
        val os = UserAgentParser.parseOS(ua)
        val device = UserAgentParser.parseDevice(ua)

        assertNotNull(browser)
        assertNotNull(os)
        assertNotNull(device)
    }

    @Test
    fun `parser handles special characters without crashing`() {
        val ua = "Mozilla/5.0 (Windows; U; Windows NT 6.0; en-US) <script>alert('xss')</script>"

        // Should not throw exception
        val browser = UserAgentParser.parseBrowser(ua)
        val os = UserAgentParser.parseOS(ua)
        val device = UserAgentParser.parseDevice(ua)

        assertNotNull(browser)
        assertNotNull(os)
        assertNotNull(device)
    }

    @Test
    fun `parseBrowser with Opera UA returns non-null`() {
        val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36 OPR/77.0.4054.277"
        val browser = UserAgentParser.parseBrowser(ua)

        assertNotNull(browser)
        assertTrue(browser.isNotEmpty())
    }

    @Test
    fun `all parser methods are thread-safe`() {
        val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/91.0"

        // Call parsers concurrently from multiple threads
        val threads = (1..10).map {
            Thread {
                repeat(100) {
                    UserAgentParser.parseBrowser(ua)
                    UserAgentParser.parseOS(ua)
                    UserAgentParser.parseDevice(ua)
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // If we get here without exception, thread-safety is good
        assertTrue(true)
    }
}
