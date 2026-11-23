package se.onemanstudio.services

/**
 * Rolling traffic-spike rule, kept in memory per project (counts only, nothing about visitors).
 *
 * Visits are counted per minute. The current window is the last [WINDOW_MINUTES] minutes; the
 * baseline is the average of the [BASELINE_WINDOWS] windows of the same length before it (the
 * previous hour). A spike is a current window with at least [MIN_VISITS] visits and more than
 * [FACTOR] times the baseline average. After a spike the project is quiet for one window
 * (cooldown), so a webhook fires at most once per window. Nothing fires until the detector has
 * watched for a full baseline plus one window (70 minutes after start), because a restart wipes
 * the history and every busy site would otherwise look like a spike.
 *
 * [clock] returns epoch milliseconds; tests inject their own.
 */
class TrafficSpikeDetector(private val clock: () -> Long = System::currentTimeMillis) {
    data class Spike(val windowVisits: Int, val baselinePerWindow: Double)

    private class Project {
        val perMinute = java.util.TreeMap<Long, Int>()
        var lastFiredMinute = Long.MIN_VALUE / 2 // far in the past, without overflowing
    }

    private val startMinute = clock() / MILLIS_PER_MINUTE
    private val projects = HashMap<String, Project>()

    /** Count one visit for [key]; returns the spike if this visit completes one. */
    @Synchronized
    fun record(key: String): Spike? {
        val minute = clock() / MILLIS_PER_MINUTE
        val project = projects.getOrPut(key) { Project() }
        project.perMinute.merge(minute, 1, Int::plus)
        project.perMinute.headMap(minute - HISTORY_MINUTES + 1).clear()

        if (minute - startMinute < HISTORY_MINUTES) return null
        if (minute - project.lastFiredMinute < WINDOW_MINUTES) return null

        val current = project.perMinute.subMap(minute - WINDOW_MINUTES + 1, minute + 1).values.sum()
        val before = project.perMinute.subMap(minute - HISTORY_MINUTES + 1, minute - WINDOW_MINUTES + 1).values.sum()
        val baseline = before.toDouble() / BASELINE_WINDOWS
        if (current < MIN_VISITS || current <= baseline * FACTOR) return null

        project.lastFiredMinute = minute
        return Spike(current, baseline)
    }

    companion object {
        const val WINDOW_MINUTES = 10L
        const val BASELINE_WINDOWS = 6
        const val MIN_VISITS = 20
        const val FACTOR = 2.0
        private const val HISTORY_MINUTES = WINDOW_MINUTES * (BASELINE_WINDOWS + 1)
        private const val MILLIS_PER_MINUTE = 60_000L
    }
}
