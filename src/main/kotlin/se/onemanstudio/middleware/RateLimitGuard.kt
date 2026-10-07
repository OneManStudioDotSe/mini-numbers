package se.onemanstudio.middleware

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import se.onemanstudio.api.models.ApiError
import se.onemanstudio.middleware.models.RateLimitResult

/**
 * Apply a [RateLimiter] to the current call, keyed by client IP plus a fixed [scope]
 * (e.g. "auth", "admin") that acts as a global bucket for that endpoint group.
 * Responds with 429 and returns false when a limit is exceeded.
 */
suspend fun ApplicationCall.enforceRateLimit(limiter: RateLimiter, scope: String): Boolean {
    val ip = request.origin.remoteHost
    val result = limiter.checkRateLimit(ip, scope)
    if (result is RateLimitResult.Exceeded) {
        application.environment.log.warn(
            "Rate limit exceeded on $scope: ${result.limitType} - ${result.identifier}"
        )
        respond(
            HttpStatusCode.TooManyRequests,
            ApiError.rateLimited(
                "Too many requests. Please try again later.",
                result.limitType,
                result.limit,
                result.window
            )
        )
        return false
    }
    return true
}
