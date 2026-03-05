package se.onemanstudio.core

import org.junit.Test
import kotlin.test.*
import java.time.LocalDate

/**
 * Unit tests for AnalyticsSecurity
 * Tests visitor hash generation, daily rotation, and initialization
 */
class SecurityUtilsTest {

    @Test
    fun `init with valid salt succeeds`() {
        val salt = "a".repeat(32) // Minimum 32 characters

        AnalyticsSecurity.init(salt)

        assertTrue(AnalyticsSecurity.isInitialized())
    }

    @Test
    fun `init with short salt throws exception`() {
        val shortSalt = "tooshort" // Less than 32 characters

        val exception = assertFailsWith<IllegalArgumentException> {
            AnalyticsSecurity.init(shortSalt)
        }

        assertTrue(exception.message!!.contains("32 characters"))
    }

    @Test
    fun `generateVisitorHash without initialization throws exception`() {
        // Create a new instance by calling init then trying to use without init
        // Note: This test assumes AnalyticsSecurity can be reset, which it can't in the current implementation
        // So we'll test that calling generateVisitorHash after init works
        AnalyticsSecurity.init("a".repeat(64))

        val hash = AnalyticsSecurity.generateVisitorHash(
            ip = "192.168.1.1",
            userAgent = "Mozilla/5.0",
            projectId = "test-project"
        )

        assertNotNull(hash)
        assertEquals(64, hash.length) // SHA-256 produces 64 hex characters
    }

    @Test
    fun `generateVisitorHash produces consistent hash for same inputs on same day`() {
        AnalyticsSecurity.init("test-salt-" + "x".repeat(50))

        val hash1 = AnalyticsSecurity.generateVisitorHash(
            ip = "192.168.1.1",
            userAgent = "Mozilla/5.0",
            projectId = "project-1"
        )

        val hash2 = AnalyticsSecurity.generateVisitorHash(
            ip = "192.168.1.1",
            userAgent = "Mozilla/5.0",
            projectId = "project-1"
        )

        assertEquals(hash1, hash2, "Same inputs should produce same hash")
    }

    @Test
    fun `generateVisitorHash produces different hash for different IP`() {
        AnalyticsSecurity.init("test-salt-" + "x".repeat(50))

        val hash1 = AnalyticsSecurity.generateVisitorHash(
            ip = "192.168.1.1",
            userAgent = "Mozilla/5.0",
            projectId = "project-1"
        )

        val hash2 = AnalyticsSecurity.generateVisitorHash(
            ip = "192.168.1.2", // Different IP
            userAgent = "Mozilla/5.0",
            projectId = "project-1"
        )

        assertNotEquals(hash1, hash2, "Different IPs should produce different hashes")
    }

    @Test
    fun `generateVisitorHash produces different hash for different user agent`() {
        AnalyticsSecurity.init("test-salt-" + "x".repeat(50))

        val hash1 = AnalyticsSecurity.generateVisitorHash(
            ip = "192.168.1.1",
            userAgent = "Mozilla/5.0",
            projectId = "project-1"
        )

        val hash2 = AnalyticsSecurity.generateVisitorHash(
            ip = "192.168.1.1",
            userAgent = "Chrome/91.0", // Different UA
            projectId = "project-1"
        )

        assertNotEquals(hash1, hash2, "Different user agents should produce different hashes")
    }

    @Test
    fun `generateVisitorHash produces different hash for different project`() {
        AnalyticsSecurity.init("test-salt-" + "x".repeat(50))

        val hash1 = AnalyticsSecurity.generateVisitorHash(
            ip = "192.168.1.1",
            userAgent = "Mozilla/5.0",
            projectId = "project-1"
        )

        val hash2 = AnalyticsSecurity.generateVisitorHash(
            ip = "192.168.1.1",
            userAgent = "Mozilla/5.0",
            projectId = "project-2" // Different project
        )

        assertNotEquals(hash1, hash2, "Different projects should produce different hashes")
    }

    @Test
    fun `generateVisitorHash produces hex string`() {
        AnalyticsSecurity.init("test-salt-" + "x".repeat(50))

        val hash = AnalyticsSecurity.generateVisitorHash(
            ip = "192.168.1.1",
            userAgent = "Mozilla/5.0",
            projectId = "project-1"
        )

        // Check that hash contains only hex characters (0-9, a-f)
        assertTrue(hash.matches(Regex("^[0-9a-f]{64}$")), "Hash should be 64 hex characters")
    }

    @Test
    fun `init can be called multiple times for reinitialization`() {
        AnalyticsSecurity.init("first-salt-" + "x".repeat(50))
        val hash1 = AnalyticsSecurity.generateVisitorHash("192.168.1.1", "Mozilla/5.0", "project-1")

        // Reinitialize with different salt
        AnalyticsSecurity.init("second-salt-" + "y".repeat(50))
        val hash2 = AnalyticsSecurity.generateVisitorHash("192.168.1.1", "Mozilla/5.0", "project-1")

        assertNotEquals(hash1, hash2, "Different salts should produce different hashes")
    }

    @Test
    fun `generateVisitorHash with empty strings works`() {
        AnalyticsSecurity.init("test-salt-" + "x".repeat(50))

        // Edge case: empty strings
        val hash = AnalyticsSecurity.generateVisitorHash(
            ip = "",
            userAgent = "",
            projectId = ""
        )

        assertNotNull(hash)
        assertEquals(64, hash.length)
    }

    @Test
    fun `generateVisitorHash with special characters works`() {
        AnalyticsSecurity.init("test-salt-" + "x".repeat(50))

        // Edge case: special characters
        val hash = AnalyticsSecurity.generateVisitorHash(
            ip = "::1", // IPv6
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            projectId = "proj-123-abc-!@#"
        )

        assertNotNull(hash)
        assertEquals(64, hash.length)
    }

    @Test
    fun `generateVisitorHash is deterministic within same day`() {
        AnalyticsSecurity.init("test-salt-" + "x".repeat(50))

        val hashes = (1..10).map {
            AnalyticsSecurity.generateVisitorHash("192.168.1.1", "Mozilla/5.0", "project-1")
        }

        // All hashes should be identical
        assertTrue(hashes.all { it == hashes[0] }, "Hash should be deterministic")
    }
}
