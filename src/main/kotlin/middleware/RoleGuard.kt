package se.onemanstudio.middleware

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import se.onemanstudio.api.models.ApiError
import se.onemanstudio.core.models.UserRole
import se.onemanstudio.core.models.UserSession

/**
 * Check if the current user has one of the required roles.
 * Works with both session auth and JWT auth.
 * Returns true if authorized, false if blocked (response already sent).
 */
suspend fun ApplicationCall.requireRole(vararg roles: UserRole): Boolean {
    val userRole = getUserRole()

    if (userRole == null || userRole !in roles) {
        respond(HttpStatusCode.Forbidden,
            ApiError(error = "Insufficient permissions", code = "FORBIDDEN"))
        return false
    }
    return true
}

/**
 * Extract the user's role from the current authentication principal.
 * Checks session first, then JWT.
 */
fun ApplicationCall.getUserRole(): UserRole? {
    // Try session auth
    sessions.get<UserSession>()?.let { session ->
        return try {
            UserRole.valueOf(session.role.uppercase())
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    // Try JWT auth
    principal<JWTPrincipal>()?.let { jwt ->
        val role = jwt.payload.getClaim("role")?.asString()
        return try {
            UserRole.valueOf(role?.uppercase() ?: return null)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    return null
}
