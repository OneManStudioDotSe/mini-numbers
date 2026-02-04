package se.onemanstudio.utils

import eu.bitwalker.useragentutils.UserAgent

object UserAgentParser {
    /**
     * Parse browser name and version from User-Agent string
     * Returns format like "Chrome 120", "Firefox 121", "Safari 17"
     */
    fun parseBrowser(userAgentString: String): String {
        return try {
            val ua = UserAgent.parseUserAgentString(userAgentString)
            val browser = ua.browser
            val version = ua.browserVersion?.majorVersion ?: ""

            if (version.isNotEmpty()) {
                "${browser.name} $version"
            } else {
                browser.name
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }

    /**
     * Parse operating system from User-Agent string
     */
    fun parseOS(userAgentString: String): String {
        return try {
            val ua = UserAgent.parseUserAgentString(userAgentString)
            ua.operatingSystem.name
        } catch (e: Exception) {
            "Unknown"
        }
    }

    /**
     * Parse device type from User-Agent string
     */
    fun parseDevice(userAgentString: String): String {
        return try {
            val ua = UserAgent.parseUserAgentString(userAgentString)
            ua.operatingSystem.deviceType.name
        } catch (e: Exception) {
            "Unknown"
        }
    }
}
