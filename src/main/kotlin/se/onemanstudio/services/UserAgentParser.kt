package se.onemanstudio.services

import ua_parser.Client
import ua_parser.Parser

/**
 * Browser / OS / device detection backed by uap-java (uap-core regexes, Apache-2.0).
 *
 * Output vocabulary matches the demo data generator and the dashboard icon mapping:
 * browser `"Chrome 120"`, OS `"Windows"` / `"macOS"` / `"iOS"` / `"Android"` / `"Linux"`,
 * device `"Desktop"` / `"Mobile"` / `"Tablet"` / `"Bot"`, and `"Unknown"` when unparseable.
 */
object UserAgentParser {
    private val parser = Parser()
    private const val OTHER = "Other"
    private const val UNKNOWN = "Unknown"
    private val DESKTOP_OS = setOf("Windows", "Mac OS X", "Linux", "Ubuntu", "Chrome OS", "Fedora", "Debian", "FreeBSD")
    private val MOBILE_OS = setOf("iOS", "Android")

    /**
     * Parse browser name and major version from User-Agent string
     * Returns format like "Chrome 120", "Firefox 121", "Safari 17"
     */
    fun parseBrowser(userAgentString: String): String {
        val client = parse(userAgentString) ?: return UNKNOWN
        val family = client.userAgent.family?.takeUnless { it.isBlank() || it == OTHER } ?: return UNKNOWN
        val major = client.userAgent.major
        return if (major.isNullOrBlank()) family else "$family $major"
    }

    /**
     * Parse operating system from User-Agent string
     */
    fun parseOS(userAgentString: String): String {
        val family = parse(userAgentString)?.os?.family?.takeUnless { it.isBlank() || it == OTHER } ?: return UNKNOWN
        return if (family == "Mac OS X") "macOS" else family
    }

    /**
     * Parse device type from User-Agent string
     */
    fun parseDevice(userAgentString: String): String {
        val client = parse(userAgentString) ?: return UNKNOWN
        val device = client.device.family.orEmpty()
        val os = client.os.family.orEmpty()
        val ua = userAgentString.lowercase()
        return when {
            device == "Spider" -> "Bot"
            device.contains("iPad") || device.contains("Tablet") ||
                (os == "Android" && !ua.contains("mobile")) -> "Tablet"
            device.contains("iPhone") || device.contains("Phone") ||
                ua.contains("mobile") || os in MOBILE_OS -> "Mobile"
            os in DESKTOP_OS -> "Desktop"
            else -> UNKNOWN
        }
    }

    private fun parse(userAgentString: String): Client? {
        if (userAgentString.isBlank()) return null
        return try {
            parser.parse(userAgentString)
        } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
            null
        }
    }
}
