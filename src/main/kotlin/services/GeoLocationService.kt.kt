package se.onemanstudio.services

import com.maxmind.geoip2.DatabaseReader
import org.slf4j.LoggerFactory
import java.io.File
import java.net.InetAddress

object GeoLocationService {
    private val logger = LoggerFactory.getLogger(GeoLocationService::class.java)
    private var reader: DatabaseReader? = null

    fun init(dbPath: String) {
        // Close existing reader if reinitializing
        close()

        // 1. Try filesystem path first (works in dev mode and custom paths)
        val database = File(dbPath)
        if (database.exists()) {
            reader = DatabaseReader.Builder(database).build()
            logger.info("GeoIP database initialized from filesystem: $dbPath")
            return
        }

        // 2. Try classpath resource (works from fat JAR)
        val classpathPath = "geo/geolite2-city.mmdb"
        val resourceStream = this::class.java.classLoader.getResourceAsStream(classpathPath)
        if (resourceStream != null) {
            try {
                val tempFile = File.createTempFile("geolite2-city", ".mmdb")
                tempFile.deleteOnExit()
                resourceStream.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                reader = DatabaseReader.Builder(tempFile).build()
                logger.info("GeoIP database initialized from classpath (extracted to temp file)")
                return
            } catch (e: Exception) {
                logger.warn("Failed to extract GeoIP database from classpath: ${e.message}")
            }
        }

        // 3. Neither worked
        logger.warn("GeoIP database not found at '$dbPath' or on classpath. Location tracking will be disabled.")
    }

    fun lookup(ipString: String): Pair<String?, String?> {
        if (reader == null || ipString == "127.0.0.1" || ipString == "0:0:0:0:0:0:0:1") {
            return null to null
        }

        return try {
            val ipAddress = InetAddress.getByName(ipString)
            val response = reader?.city(ipAddress)
            val country = response?.country()?.isoCode() // e.g., "US", "GR"
            val city = response?.city()?.name() // e.g., "Athens"
            country to city
        } catch (e: Exception) {
            // IP not in database or private IP
            null to null
        }
    }

    /**
     * Close GeoIP database reader and release resources
     * Safe to call multiple times
     */
    fun close() {
        try {
            reader?.close()
            reader = null
            logger.debug("GeoIP database reader closed")
        } catch (e: Exception) {
            logger.error("Error closing GeoIP database reader: ${e.message}")
        }
    }
}
