package se.onemanstudio.utils

import java.util.UUID

fun safeParseUUID(value: String?): UUID? {
    if (value == null) return null
    return try {
        UUID.fromString(value)
    } catch (_: IllegalArgumentException) {
        null
    }
}
