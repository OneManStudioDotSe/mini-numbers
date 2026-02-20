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

        val database = File(dbPath)
        if (!database.exists()) {
            logger.warn("GeoIP Database not found at $dbPath. Location tracking will be disabled.")
            return
        }
        // TODO: Improve the speed of the lookup
        reader = DatabaseReader.Builder(database).build()
        logger.info("GeoIP database initialized successfully")
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
