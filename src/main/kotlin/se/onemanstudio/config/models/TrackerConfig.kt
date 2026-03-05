package se.onemanstudio.config.models

/**
 * Tracker script configuration
 * Controls heartbeat interval and SPA tracking behavior
 */
data class TrackerConfig(
    val heartbeatIntervalSeconds: Int = 30,
    val spaTrackingEnabled: Boolean = true
)
