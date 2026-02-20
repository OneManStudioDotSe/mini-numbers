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
    STRICT,    // No city-level geolocation, no browser/OS details
    PARANOID   // No geolocation at all, no user agent parsing, minimal data
}
