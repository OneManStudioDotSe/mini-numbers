package se.onemanstudio.integration

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.Test
import se.onemanstudio.module
import kotlin.test.*

/**
 * Integration tests for Setup Wizard
 * Tests setup status, salt generation, and configuration save
 */
class SetupWizardTest {

    @Test
    fun `GET setup api status returns valid response`() = testApplication {
        application { module() }

        val response = client.get("/setup/api/status")

        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.bodyAsText()
        assertTrue(body.contains("setupNeeded"))
        assertTrue(body.contains("servicesReady"))
        assertTrue(body.contains("message"))
    }

    @Test
    fun `GET setup api status returns JSON`() = testApplication {
        application { module() }

        val response = client.get("/setup/api/status")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.contentType()?.match(ContentType.Application.Json) == true)
    }

    @Test
    fun `GET setup api generate-salt returns salt`() = testApplication {
        application { module() }

        val response = client.get("/setup/api/generate-salt")

        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.bodyAsText()
        assertTrue(body.contains("salt"))
    }

    @Test
    fun `GET setup api generate-salt returns sufficient length salt`() = testApplication {
        application { module() }

        val response = client.get("/setup/api/generate-salt")
        val body = response.bodyAsText()

        // Salt should be at least 64 characters (as per implementation)
        assertTrue(body.length > 64)
    }

    @Test
    fun `POST setup api save with invalid JSON returns 400`() = testApplication {
        application { module() }

        val response = client.post("/setup/api/save") {
            contentType(ContentType.Application.Json)
            setBody("invalid json {{{")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST setup api save with missing required fields returns validation error`() = testApplication {
        application { module() }

        val response = client.post("/setup/api/save") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "adminUsername": "admin"
                }
            """.trimIndent())
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("valid") || body.contains("error"))
    }

    @Test
    fun `POST setup api save with short password returns validation error`() = testApplication {
        application { module() }

        val response = client.post("/setup/api/save") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "adminUsername": "admin",
                    "adminPassword": "short",
                    "serverSalt": "${"x".repeat(64)}",
                    "allowedOrigins": "",
                    "database": {
                        "type": "sqlite",
                        "sqlitePath": "./test.db"
                    },
                    "server": {
                        "port": 8080,
                        "isDevelopment": true
                    },
                    "geoip": {
                        "databasePath": "./geo.mmdb"
                    },
                    "rateLimit": {
                        "perIp": 1000,
                        "perApiKey": 10000
                    }
                }
            """.trimIndent())
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("password") || body.contains("valid"))
    }

    @Test
    fun `POST setup api save with short salt returns validation error`() = testApplication {
        application { module() }

        val response = client.post("/setup/api/save") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "adminUsername": "admin",
                    "adminPassword": "securepassword123",
                    "serverSalt": "tooshort",
                    "allowedOrigins": "",
                    "database": {
                        "type": "sqlite",
                        "sqlitePath": "./test.db"
                    },
                    "server": {
                        "port": 8080,
                        "isDevelopment": true
                    },
                    "geoip": {
                        "databasePath": "./geo.mmdb"
                    },
                    "rateLimit": {
                        "perIp": 1000,
                        "perApiKey": 10000
                    }
                }
            """.trimIndent())
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("salt") || body.contains("valid"))
    }

    @Test
    fun `POST setup api save with invalid port returns validation error`() = testApplication {
        application { module() }

        val response = client.post("/setup/api/save") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "adminUsername": "admin",
                    "adminPassword": "securepassword123",
                    "serverSalt": "${"x".repeat(64)}",
                    "allowedOrigins": "",
                    "database": {
                        "type": "sqlite",
                        "sqlitePath": "./test.db"
                    },
                    "server": {
                        "port": 999999,
                        "isDevelopment": true
                    },
                    "geoip": {
                        "databasePath": "./geo.mmdb"
                    },
                    "rateLimit": {
                        "perIp": 1000,
                        "perApiKey": 10000
                    }
                }
            """.trimIndent())
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("port") || body.contains("valid"))
    }

    @Test
    fun `POST setup api save with negative rate limit returns validation error`() = testApplication {
        application { module() }

        val response = client.post("/setup/api/save") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "adminUsername": "admin",
                    "adminPassword": "securepassword123",
                    "serverSalt": "${"x".repeat(64)}",
                    "allowedOrigins": "",
                    "database": {
                        "type": "sqlite",
                        "sqlitePath": "./test.db"
                    },
                    "server": {
                        "port": 8080,
                        "isDevelopment": true
                    },
                    "geoip": {
                        "databasePath": "./geo.mmdb"
                    },
                    "rateLimit": {
                        "perIp": -100,
                        "perApiKey": 10000
                    }
                }
            """.trimIndent())
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST setup api save with PostgreSQL requires connection details`() = testApplication {
        application { module() }

        val response = client.post("/setup/api/save") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "adminUsername": "admin",
                    "adminPassword": "securepassword123",
                    "serverSalt": "${"x".repeat(64)}",
                    "allowedOrigins": "",
                    "database": {
                        "type": "postgresql"
                    },
                    "server": {
                        "port": 8080,
                        "isDevelopment": true
                    },
                    "geoip": {
                        "databasePath": "./geo.mmdb"
                    },
                    "rateLimit": {
                        "perIp": 1000,
                        "perApiKey": 10000
                    }
                }
            """.trimIndent())
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET setup root serves wizard HTML`() = testApplication {
        application { module() }

        val response = client.get("/setup")

        assertEquals(HttpStatusCode.OK, response.status)
        // Should serve HTML
        val body = response.bodyAsText()
        assertTrue(body.contains("html") || body.contains("HTML") || body.contains("<!DOCTYPE"))
    }

    @Test
    fun `GET setup with trailing slash works`() = testApplication {
        application { module() }

        val response = client.get("/setup/")

        // Should redirect or serve content
        assertTrue(
            response.status == HttpStatusCode.OK ||
            response.status == HttpStatusCode.MovedPermanently ||
            response.status == HttpStatusCode.Found
        )
    }

    @Test
    fun `setup api endpoints require POST for save`() = testApplication {
        application { module() }

        val response = client.get("/setup/api/save")

        // Should not allow GET for save endpoint
        assertNotEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `setup api endpoints return JSON content type`() = testApplication {
        application { module() }

        val response = client.get("/setup/api/status")

        assertEquals(HttpStatusCode.OK, response.status)
        assertNotNull(response.contentType())
        assertTrue(response.contentType()?.match(ContentType.Application.Json) == true)
    }

    @Test
    fun `multiple calls to generate-salt return different salts`() = testApplication {
        application { module() }

        val response1 = client.get("/setup/api/generate-salt")
        val response2 = client.get("/setup/api/generate-salt")

        val body1 = response1.bodyAsText()
        val body2 = response2.bodyAsText()

        assertNotEquals(body1, body2, "Each call should generate a unique salt")
    }
}
