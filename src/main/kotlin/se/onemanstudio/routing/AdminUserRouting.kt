package se.onemanstudio.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import se.onemanstudio.api.models.ApiError
import se.onemanstudio.api.models.admin.CreateUserRequest
import se.onemanstudio.api.models.admin.UpdateUserRoleRequest
import se.onemanstudio.api.models.admin.UserResponse
import se.onemanstudio.core.models.UserRole
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
        val hashedPassword = org.mindrot.jbcrypt.BCrypt.hashpw(
            request.password,
            org.mindrot.jbcrypt.BCrypt.gensalt(12)
        )

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

    // Delete a user
    delete("/users/{userId}") {
        if (!call.requireRole(UserRole.ADMIN)) return@delete
        val userId = safeParseUUID(call.parameters["userId"])
            ?: return@delete call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid or missing user ID"))

        val deleted = transaction {
            Users.deleteWhere {
                Users.id eq userId
            } > 0
        }

        if (deleted) {
            call.respond(HttpStatusCode.NoContent)
        } else {
            call.respond(HttpStatusCode.NotFound, ApiError.notFound("User not found"))
        }
    }
}
