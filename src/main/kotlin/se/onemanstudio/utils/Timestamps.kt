package se.onemanstudio.utils

import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Date-times are stored as the server's wall-clock time. The API sends them as ISO-8601
 * with the server's UTC offset (`2026-11-03T02:24:52.145+01:00`), so a browser in any zone
 * reads the right instant. Bucket labels (hour, day) stay plain and mean the server's zone.
 */
fun LocalDateTime.toApiString(): String = atZone(ZoneId.systemDefault()).toOffsetDateTime().toString()

/** The current instant in the API's timestamp format. */
fun nowApiString(): String = LocalDateTime.now().toApiString()
