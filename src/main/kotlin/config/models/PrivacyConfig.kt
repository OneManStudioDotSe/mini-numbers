package se.onemanstudio.config.models

/**
 * Privacy-related configuration
 * Controls hash rotation, privacy mode levels, and data retention
 */
data class PrivacyConfig(
    val hashRotationHours: Int = 24,
    val privacyMode: PrivacyMode = PrivacyMode.STANDARD,
    val dataRetentionDays: Int = 0 // 0 = keep forever
)

/**
 * Privacy mode levels controlling what data is collected
 */
enum class PrivacyMode {
    STANDARD,  // Default: collects all analytics (country, city, browser, OS, device)
    STRICT,    // Country-only geolocation (no city/region/coordinates), full browser/OS/device
    PARANOID   // No geolocation at all, no user agent parsing, minimal data
}
