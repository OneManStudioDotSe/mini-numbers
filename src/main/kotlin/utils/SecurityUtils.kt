package se.onemanstudio.utils

import java.security.MessageDigest
import java.time.LocalDate

object AnalyticsSecurity {
    // In a real app, load this from an Environment Variable
    private const val SERVER_SALT = "change-this-to-a-long-secret-string"

    fun generateVisitorHash(ip: String, userAgent: String, projectId: String): String {
        val dateSalt = LocalDate.now().toString() // Hash changes every day
        val input = ip + userAgent + projectId + SERVER_SALT + dateSalt

        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
