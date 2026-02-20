package se.onemanstudio.setup.models

import kotlinx.serialization.Serializable

/**
 * Setup status response
 * Includes both setup status and service readiness
 */
@Serializable
data class SetupStatusResponse(
    val setupNeeded: Boolean,
    val servicesReady: Boolean,
    val message: String
)
