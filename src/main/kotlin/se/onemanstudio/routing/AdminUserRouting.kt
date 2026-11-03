package se.onemanstudio.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import se.onemanstudio.api.models.ApiError
import se.onemanstudio.api.models.admin.CreateUserRequest
import se.onemanstudio.api.models.admin.UpdateUserActiveRequest
import se.onemanstudio.api.models.admin.UpdateUserRoleRequest
import se.onemanstudio.api.models.admin.UserResponse
import se.onemanstudio.core.models.UserRole
import se.onemanstudio.core.models.UserSession
import se.onemanstudio.db.RefreshTokens
import se.onemanstudio.db.Users
import se.onemanstudio.middleware.requireRole
import java.util.UUID

fun Route.adminUserRoutes() {
    // List all users
    get("/users") {
        if (!call.requireRole(UserRole.ADMIN)) return@get
        val users = transaction {
            Users.selectAll().map {
                UserResponse(
                    id = it[Users.id].toString(),
                    username = it[Users.username],
                    role = it[Users.role],
                    isActive = it[Users.isActive],
                    createdAt = it[Users.createdAt].toString()
                )
            }
        }
        call.respond(users)
    }

    // Create a new user
    post("/users") {
        if (!call.requireRole(UserRole.ADMIN)) return@post
        val request = try {
            call.receive<CreateUserRequest>()
        } catch (_: io.ktor.server.plugins.ContentTransformationException) {
            return@post call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid request body"))
        }

        if (request.username.isBlank() || request.username.length > 100) {
            return@post call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Username must be 1-100 characters"))
        }
        if (request.password.length < 8) {
            return@post call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Password must be at least 8 characters"))
        }
        if (request.role !in listOf("admin", "viewer")) {
            return@post call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Role must be 'admin' or 'viewer'"))
        }

        // Check for duplicate username
        val exists = transaction {
            Users.selectAll().where {
                Users.username eq request.username
            }.count() > 0
        }
        if (exists) {
            return@post call.respond(HttpStatusCode.Conflict,
                ApiError(error = "Username already exists", code = "CONFLICT"))
        }

        val userId = UUID.randomUUID()
        val hashedPassword = se.onemanstudio.core.Passwords.hash(request.password)

        transaction {
            Users.insert {
                it[id] = userId
                it[username] = request.username
                it[passwordHash] = hashedPassword
                it[role] = request.role
            }
        }

        call.respond(HttpStatusCode.Created, buildJsonObject {
            put("id", userId.toString())
            put("success", true)
        })
    }

    // Update user role
    put("/users/{userId}/role") {
        if (!call.requireRole(UserRole.ADMIN)) return@put
        val userId = safeParseUUID(call.parameters["userId"])
            ?: return@put call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing user ID"))

        val request = try {
            call.receive<UpdateUserRoleRequest>()
        } catch (_: io.ktor.server.plugins.ContentTransformationException) {
            return@put call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid request body"))
        }

        if (request.role !in listOf("admin", "viewer")) {
            return@put call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Role must be 'admin' or 'viewer'"))
        }
        if (call.refuseIfSelf(userId)) return@put

        val updated = transaction {
            Users.update({
                Users.id eq userId
            }) {
                it[role] = request.role
            }
        }

        if (updated > 0) {
            call.respond(HttpStatusCode.OK, buildJsonObject { put("success", true) })
        } else {
            call.respond(HttpStatusCode.NotFound, ApiError.notFound("User not found"))
        }
    }

    // Deactivate or reactivate a user; their sessions and tokens stop working on the next request
    put("/users/{userId}/active") {
        if (!call.requireRole(UserRole.ADMIN)) return@put
        val userId = safeParseUUID(call.parameters["userId"])
            ?: return@put call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing user ID"))

        val request = try {
            call.receive<UpdateUserActiveRequest>()
        } catch (_: io.ktor.server.plugins.ContentTransformationException) {
            return@put call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid request body"))
        }
        if (call.refuseIfSelf(userId)) return@put

        val found = transaction {
            val username = Users.selectAll().where { Users.id eq userId }.singleOrNull()?.get(Users.username)
                ?: return@transaction false
            Users.update({ Users.id eq userId }) { it[isActive] = request.isActive }
            if (!request.isActive) {
                RefreshTokens.deleteWhere { RefreshTokens.username eq username }
            }
            true
        }

        if (found) {
            call.respond(HttpStatusCode.OK, buildJsonObject { put("success", true) })
        } else {
            call.respond(HttpStatusCode.NotFound, ApiError.notFound("User not found"))
        }
    }

    // Delete a user and their refresh tokens
    delete("/users/{userId}") {
        if (!call.requireRole(UserRole.ADMIN)) return@delete
        val userId = safeParseUUID(call.parameters["userId"])
            ?: return@delete call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing user ID"))
        if (call.refuseIfSelf(userId)) return@delete

        val deleted = transaction {
            val username = Users.selectAll().where { Users.id eq userId }.singleOrNull()?.get(Users.username)
                ?: return@transaction false
            RefreshTokens.deleteWhere { RefreshTokens.username eq username }
            Users.deleteWhere { Users.id eq userId } > 0
        }

        if (deleted) {
            call.respond(HttpStatusCode.NoContent)
        } else {
            call.respond(HttpStatusCode.NotFound, ApiError.notFound("User not found"))
        }
    }
}

/** The username of the authenticated caller, from the session cookie or the JWT. */
private fun ApplicationCall.callerUsername(): String? =
    principal<UserSession>()?.username ?: principal<JWTPrincipal>()?.payload?.subject

/**
 * Admins may not demote, deactivate or delete their own account. The last active admin is
 * the only one who could act on themselves, so this is also what keeps one admin alive.
 * Responds 409 and returns true when the target is the caller.
 */
private suspend fun ApplicationCall.refuseIfSelf(userId: UUID): Boolean {
    val target = transaction { Users.selectAll().where { Users.id eq userId }.singleOrNull()?.get(Users.username) }
    if (target == null || target != callerUsername()) return false
    respond(HttpStatusCode.Conflict,
        ApiError(error = "You cannot change your own account; ask another admin", code = "CONFLICT"))
    return true
}
