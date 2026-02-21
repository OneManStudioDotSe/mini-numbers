package se.onemanstudio.setup

import org.junit.Test
import se.onemanstudio.setup.models.*
import kotlin.test.*

/**
 * Unit tests for SetupValidation
 * Tests server-side validation of setup wizard configuration
 */
class SetupValidationTest {

    private fun validConfig() = SetupConfigDTO(
        adminUsername = "admin",
        adminPassword = "securepassword123",
        serverSalt = "x".repeat(64),
        allowedOrigins = "",
        database = DatabaseSetupDTO(
            type = "sqlite",
            sqlitePath = "./test.db"
        ),
        server = ServerSetupDTO(port = 8080, isDevelopment = true),
        geoip = GeoIPSetupDTO(databasePath = "./geo.mmdb"),
        rateLimit = RateLimitSetupDTO(perIp = 1000, perApiKey = 10000)
    )

    // ==================== Admin Username Validation ====================

    @Test
    fun `valid config passes validation`() {
        val result = SetupValidation.validateSetupConfig(validConfig())
        assertTrue(result.valid, "Valid config should pass: ${result.errors}")
    }

    @Test
    fun `blank username fails validation`() {
        val config = validConfig().copy(adminUsername = "")
        val result = SetupValidation.validateSetupConfig(config)
        assertFalse(result.valid)
        assertTrue(result.errors.containsKey("adminUsername"))
    }

    @Test
    fun `short username fails validation`() {
        val config = validConfig().copy(adminUsername = "ab")
        val result = SetupValidation.validateSetupConfig(config)
        assertFalse(result.valid)
        assertTrue(result.errors.containsKey("adminUsername"))
    }

    @Test
    fun `username with special characters fails validation`() {
        val config = validConfig().copy(adminUsername = "admin@user!")
        val result = SetupValidation.validateSetupConfig(config)
        assertFalse(result.valid)
        assertTrue(result.errors.containsKey("adminUsername"))
    }

    @Test
    fun `username with hyphens and underscores passes validation`() {
        val config = validConfig().copy(adminUsername = "admin-user_1")
        val result = SetupValidation.validateSetupConfig(config)
        assertTrue(result.valid, "Hyphens and underscores should be allowed")
    }

    // ==================== Admin Password Validation ====================

    @Test
    fun `short password fails validation`() {
        val config = validConfig().copy(adminPassword = "short")
        val result = SetupValidation.validateSetupConfig(config)
        assertFalse(result.valid)
        assertTrue(result.errors.containsKey("adminPassword"))
    }

    @Test
    fun `blank password fails validation`() {
        val config = validConfig().copy(adminPassword = "")
        val result = SetupValidation.validateSetupConfig(config)
        assertFalse(result.valid)
        assertTrue(result.errors.containsKey("adminPassword"))
    }

    @Test
    fun `password of exactly 8 chars passes validation`() {
        val config = validConfig().copy(adminPassword = "12345678")
        val result = SetupValidation.validateSetupConfig(config)
        assertTrue(result.valid, "8-char password should pass")
    }

    // ==================== Server Salt Validation ====================

    @Test
    fun `short salt fails validation`() {
        val config = validConfig().copy(serverSalt = "tooshort")
        val result = SetupValidation.validateSetupConfig(config)
        assertFalse(result.valid)
        assertTrue(result.errors.containsKey("serverSalt"))
    }

    @Test
    fun `salt of exactly 32 chars passes validation`() {
        val config = validConfig().copy(serverSalt = "a".repeat(32))
        val result = SetupValidation.validateSetupConfig(config)
        assertTrue(result.valid, "32-char salt should pass")
    }

    @Test
    fun `salt exceeding 128 chars fails validation`() {
        val config = validConfig().copy(serverSalt = "a".repeat(129))
        val result = SetupValidation.validateSetupConfig(config)
        assertFalse(result.valid)
        assertTrue(result.errors.containsKey("serverSalt"))
    }

    // ==================== Allowed Origins Validation ====================

    @Test
    fun `empty allowed origins passes validation`() {
        val config = validConfig().copy(allowedOrigins = "")
        val result = SetupValidation.validateSetupConfig(config)
        assertTrue(result.valid)
    }

    @Test
    fun `wildcard origin passes validation`() {
        val config = validConfig().copy(allowedOrigins = "*")
        val result = SetupValidation.validateSetupConfig(config)
        assertTrue(result.valid)
    }

    @Test
    fun `valid HTTPS origin passes validation`() {
        val config = validConfig().copy(allowedOrigins = "https://example.com")
        val result = SetupValidation.validateSetupConfig(config)
        assertTrue(result.valid)
    }

    @Test
    fun `invalid origin format fails validation`() {
        val config = validConfig().copy(allowedOrigins = "not-a-url")
        val result = SetupValidation.validateSetupConfig(config)
        assertFalse(result.valid)
        assertTrue(result.errors.containsKey("allowedOrigins"))
    }

    // ==================== Database Validation ====================

    @Test
    fun `SQLite with valid path passes`() {
        val config = validConfig().copy(
            database = DatabaseSetupDTO(type = "sqlite", sqlitePath = "./stats.db")
        )
        val result = SetupValidation.validateSetupConfig(config)
        assertTrue(result.valid)
    }

    @Test
    fun `SQLite with blank path fails`() {
        val config = validConfig().copy(
            database = DatabaseSetupDTO(type = "sqlite", sqlitePath = "")
        )
        val result = SetupValidation.validateSetupConfig(config)
        assertFalse(result.valid)
        assertTrue(result.errors.containsKey("dbSqlitePath"))
    }

    @Test
    fun `PostgreSQL without host fails`() {
        val config = validConfig().copy(
            database = DatabaseSetupDTO(
                type = "postgresql",
                pgHost = "",
                pgPort = 5432,
                pgName = "mydb",
                pgUsername = "user",
                pgPassword = "pass"
            )
        )
        val result = SetupValidation.validateSetupConfig(config)
        assertFalse(result.valid)
        assertTrue(result.errors.containsKey("dbPgHost"))
    }

    @Test
    fun `PostgreSQL with invalid port fails`() {
        val config = validConfig().copy(
            database = DatabaseSetupDTO(
                type = "postgresql",
                pgHost = "localhost",
                pgPort = 99999,
                pgName = "mydb",
                pgUsername = "user",
                pgPassword = "pass"
            )
        )
        val result = SetupValidation.validateSetupConfig(config)
        assertFalse(result.valid)
        assertTrue(result.errors.containsKey("dbPgPort"))
    }

    @Test
    fun `invalid database type fails`() {
        val config = validConfig().copy(
            database = DatabaseSetupDTO(type = "mysql")
        )
        val result = SetupValidation.validateSetupConfig(config)
        assertFalse(result.valid)
        assertTrue(result.errors.containsKey("dbType"))
    }

    // ==================== Server Validation ====================

    @Test
    fun `port below 1 fails validation`() {
        val config = validConfig().copy(
            server = ServerSetupDTO(port = 0, isDevelopment = true)
        )
        val result = SetupValidation.validateSetupConfig(config)
        assertFalse(result.valid)
        assertTrue(result.errors.containsKey("serverPort"))
    }

    @Test
    fun `port above 65535 fails validation`() {
        val config = validConfig().copy(
            server = ServerSetupDTO(port = 99999, isDevelopment = true)
        )
        val result = SetupValidation.validateSetupConfig(config)
        assertFalse(result.valid)
        assertTrue(result.errors.containsKey("serverPort"))
    }

    @Test
    fun `valid port passes validation`() {
        val config = validConfig().copy(
            server = ServerSetupDTO(port = 3000, isDevelopment = false)
        )
        val result = SetupValidation.validateSetupConfig(config)
        assertTrue(result.valid)
    }

    // ==================== Rate Limit Validation ====================

    @Test
    fun `negative IP rate limit fails validation`() {
        val config = validConfig().copy(
            rateLimit = RateLimitSetupDTO(perIp = -1, perApiKey = 10000)
        )
        val result = SetupValidation.validateSetupConfig(config)
        assertFalse(result.valid)
        assertTrue(result.errors.containsKey("rateLimitPerIp"))
    }

    @Test
    fun `zero API key rate limit fails validation`() {
        val config = validConfig().copy(
            rateLimit = RateLimitSetupDTO(perIp = 1000, perApiKey = 0)
        )
        val result = SetupValidation.validateSetupConfig(config)
        assertFalse(result.valid)
        assertTrue(result.errors.containsKey("rateLimitPerApiKey"))
    }

    @Test
    fun `rate limit exceeding 1M fails validation`() {
        val config = validConfig().copy(
            rateLimit = RateLimitSetupDTO(perIp = 2000000, perApiKey = 10000)
        )
        val result = SetupValidation.validateSetupConfig(config)
        assertFalse(result.valid)
        assertTrue(result.errors.containsKey("rateLimitPerIp"))
    }

    // ==================== Multiple Errors ====================

    @Test
    fun `validation collects multiple errors`() {
        val config = validConfig().copy(
            adminUsername = "",
            adminPassword = "short",
            serverSalt = "x"
        )
        val result = SetupValidation.validateSetupConfig(config)
        assertFalse(result.valid)
        assertTrue(result.errors.size >= 3, "Should have at least 3 errors: ${result.errors}")
    }

    // ==================== GeoIP Validation ====================

    @Test
    fun `blank geoip path fails validation`() {
        val config = validConfig().copy(
            geoip = GeoIPSetupDTO(databasePath = "")
        )
        val result = SetupValidation.validateSetupConfig(config)
        assertFalse(result.valid)
        assertTrue(result.errors.containsKey("geoipPath"))
    }

    @Test
    fun `very long geoip path fails validation`() {
        val config = validConfig().copy(
            geoip = GeoIPSetupDTO(databasePath = "a".repeat(513))
        )
        val result = SetupValidation.validateSetupConfig(config)
        assertFalse(result.valid)
        assertTrue(result.errors.containsKey("geoipPath"))
    }
}
