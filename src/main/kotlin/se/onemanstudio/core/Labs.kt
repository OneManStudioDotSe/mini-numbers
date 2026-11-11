package se.onemanstudio.core

import org.slf4j.LoggerFactory

/** Features that work but are not verified well enough to be on by default. */
enum class Lab(val key: String) {
    WIDGETS("widgets"),
    FUNNELS("funnels"),
    SEGMENTS("segments"),
    REVENUE("revenue"),
    WEBHOOKS("webhooks"),
    EMAIL_REPORTS("email-reports"),
}

/**
 * The labs switched on with `LABS` (comma-separated keys, e.g. `LABS=webhooks,funnels`).
 * Everything is off by default: the routes of a lab that is off are not mounted (404) and the dashboard hides it.
 */
object Labs {
    private val logger = LoggerFactory.getLogger(Labs::class.java)

    @Volatile
    var enabled: Set<Lab> = emptySet()
        private set

    fun isOn(lab: Lab): Boolean = lab in enabled

    fun init(labs: Set<Lab>) {
        enabled = labs
        if (labs.isNotEmpty()) logger.info("Labs switched on: ${labs.joinToString { it.key }}")
    }

    /** Parse a `LABS` value; unknown names are logged and ignored. */
    fun parse(value: String?): Set<Lab> = value.orEmpty()
        .split(',')
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
        .mapNotNull { name ->
            Lab.entries.firstOrNull { it.key == name }
                ?: null.also { logger.warn("Ignoring unknown lab '$name' in LABS (known: ${Lab.entries.joinToString { it.key }})") }
        }
        .toSet()
}
