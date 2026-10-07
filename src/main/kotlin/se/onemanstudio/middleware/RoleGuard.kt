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
import se.onemanstudio.core.resolveActiveRole

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
 * Checks session first, then JWT. For both, the database is authoritative: the role carried
 * by the cookie or the token claim is only a fallback (see [resolveActiveRole]).
 */
fun ApplicationCall.getUserRole(): UserRole? {
    // Prefer the validated session principal (role re-read on every request in Security.kt)
    // over the raw cookie, which still carries the role from login time.
    val session = principal<UserSession>() ?: sessions.get<UserSession>()
    val jwt = principal<JWTPrincipal>()

    val roleName = when {
        session != null -> session.role
        jwt != null -> jwt.payload.subject?.let { subject ->
            jwt.payload.getClaim("role")?.asString()?.let { carried -> resolveActiveRole(subject, carried) }
        }
        else -> null
    } ?: return null

    return try {
        UserRole.valueOf(roleName.uppercase())
    } catch (_: IllegalArgumentException) {
        null
    }
}
