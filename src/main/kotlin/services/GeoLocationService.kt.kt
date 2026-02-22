package se.onemanstudio.services

import com.github.benmanes.caffeine.cache.Caffeine
import com.maxmind.geoip2.DatabaseReader
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.io.File
import java.net.InetAddress
import java.util.concurrent.TimeUnit

@Serializable
data class GeoResult(
    val country: String? = null,
    val city: String? = null,
    val region: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)

object GeoLocationService {
    private val logger = LoggerFactory.getLogger(GeoLocationService::class.java)
    private var reader: DatabaseReader? = null

    // GeoIP result cache: IP -> GeoResult
    private val geoCache = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(1, TimeUnit.HOURS)
        .build<String, GeoResult>()

    fun init(dbPath: String) {
        close()

        // 1. Try filesystem path first
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

        logger.warn("GeoIP database not found at '$dbPath' or on classpath. Location tracking will be disabled.")
    }

    fun lookup(ipString: String): GeoResult {
        if (reader == null || ipString == "127.0.0.1" || ipString == "0:0:0:0:0:0:0:1") {
            return GeoResult()
        }

        // Check cache first
        return geoCache.get(ipString) {
            try {
                val ipAddress = InetAddress.getByName(ipString)
                val response = reader?.city(ipAddress)
                val country = response?.country()?.isoCode()
                val city = response?.city()?.name()
                val region = response?.mostSpecificSubdivision()?.name()
                val latitude = response?.location()?.latitude()
                val longitude = response?.location()?.longitude()
                GeoResult(country, city, region, latitude, longitude)
            } catch (e: Exception) {
                GeoResult()
            }
        }
    }

    /**
     * Get cache statistics for monitoring
     */
    fun cacheStats(): Map<String, Any> {
        return mapOf(
            "size" to geoCache.estimatedSize(),
            "hitRate" to geoCache.stats().hitRate()
        )
    }

    fun close() {
        try {
            reader?.close()
            reader = null
            geoCache.invalidateAll()
            logger.debug("GeoIP database reader closed")
        } catch (e: Exception) {
            logger.error("Error closing GeoIP database reader: ${e.message}")
        }
    }
}
