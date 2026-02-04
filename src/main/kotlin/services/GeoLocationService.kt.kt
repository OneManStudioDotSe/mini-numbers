package se.onemanstudio.services

import com.maxmind.geoip2.DatabaseReader
import java.io.File
import java.net.InetAddress

object GeoLocationService {
    private var reader: DatabaseReader? = null

    fun init(dbPath: String) {
        val database = File(dbPath)
        if (!database.exists()) {
            println("WARNING: GeoIP Database not found at $dbPath. Location tracking will be disabled.")
            return
        }
        // TODO: Improve the speed of the lookup
        reader = DatabaseReader.Builder(database).build()
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
}
