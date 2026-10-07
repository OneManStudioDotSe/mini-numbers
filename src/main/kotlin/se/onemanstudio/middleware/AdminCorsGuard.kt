package se.onemanstudio.middleware

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import se.onemanstudio.api.models.ApiError

/**
 * Validates Origin header for admin endpoints.
 * Public endpoints (/collect, /widget, /tracker) remain unrestricted
 * since they must run on third-party sites.
 */
object AdminCorsGuard {

    /**
     * Check if the request origin is allowed for admin endpoints.
     * Returns true if allowed, false if blocked (response already sent).
     *
     * Rules:
     * - No Origin header (same-origin request) → allowed
     * - Origin host:port equals the request's own Host → allowed (the admin panel
     *   itself; browsers send Origin on same-origin POST/PUT/DELETE)
     * - No allowed origins configured → allowed (development mode)
     * - Origin in allowlist → allowed
     * - Otherwise → 403 Forbidden
     */
    suspend fun check(call: ApplicationCall, allowedOrigins: List<String>): Boolean {
        val origin = call.request.header(HttpHeaders.Origin)
            ?: return true // No Origin header means same-origin request

        val originUrl = runCatching { Url(origin) }.getOrNull()
        if (originUrl != null && originUrl.host == call.request.host() && originUrl.port == call.request.port()) {
            return true
        }

        // If no origins are configured or wildcard is set, allow all (development mode)
        if (allowedOrigins.isEmpty() || allowedOrigins.any { it == "*" }) {
            return true
        }

        if (origin !in allowedOrigins) {
            call.respond(
                HttpStatusCode.Forbidden,
                ApiError(error = "Origin not allowed", code = "FORBIDDEN")
            )
            return false
        }
        return true
    }
}
