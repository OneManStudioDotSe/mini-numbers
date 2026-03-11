package se.onemanstudio.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import se.onemanstudio.api.models.ApiError
import se.onemanstudio.api.models.LoginRequest
import se.onemanstudio.api.models.admin.PasswordResetRequest
import se.onemanstudio.config.ConfigLoader
import se.onemanstudio.config.models.AppConfig
import se.onemanstudio.core.JwtService
import se.onemanstudio.core.getUserRole
import se.onemanstudio.core.models.UserRole
import se.onemanstudio.core.models.UserSession
import se.onemanstudio.core.verifyCredentials
import se.onemanstudio.db.RefreshTokens
import java.time.LocalDateTime
import java.util.UUID

fun Route.authRoutes() {
    // Serve login page
    staticResources("/login", "static/login") {
        default("login.html")
    }

    // Login endpoint
    post("/api/login") {
        val loginRequest = try {
            call.receive<LoginRequest>()
        } catch (_: io.ktor.server.plugins.ContentTransformationException) {
            return@post call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid request body"))
        }

        val currentConfig = ConfigLoader.load()
        val isValid = verifyCredentials(
            loginRequest.username,
            loginRequest.password,
            currentConfig,
            call.application.environment.log
        )

        if (isValid) {
            // Session rotation: clear any existing session before setting a new one
            call.sessions.clear<UserSession>()
            val role = getUserRole(loginRequest.username)
            call.sessions.set(UserSession(username = loginRequest.username, role = role))
            call.respond(HttpStatusCode.OK, buildJsonObject {
                put("success", true)
                put("message", "Login successful")
            })
        } else {
            call.respond(HttpStatusCode.Unauthorized,
                ApiError.unauthorized("Invalid username or password"))
        }
    }

    // Logout endpoint
    post("/api/logout") {
        call.sessions.clear<UserSession>()
        call.respond(HttpStatusCode.OK, buildJsonObject {
            put("success", true)
            put("message", "Logged out successfully")
        })
    }

    // ── JWT Token Endpoints ──────────────────────────────────────

    // Get JWT access token + refresh token
    post("/api/token") {
        val loginRequest = try {
            call.receive<LoginRequest>()
        } catch (_: io.ktor.server.plugins.ContentTransformationException) {
            return@post call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid request body"))
        }

        val tokenConfig = ConfigLoader.load()
        val isValid = verifyCredentials(
            loginRequest.username,
            loginRequest.password,
            tokenConfig,
            call.application.environment.log
        )

        if (!isValid) {
            return@post call.respond(HttpStatusCode.Unauthorized,
                ApiError.unauthorized("Invalid credentials"))
        }

        val tokenRole = getUserRole(loginRequest.username)
        val accessToken = JwtService.generateAccessToken(loginRequest.username, tokenRole)
        val refreshToken = JwtService.generateRefreshToken()
        val family = UUID.randomUUID().toString()
        val tokenId = UUID.randomUUID()

        transaction {
            RefreshTokens.insert {
                it[RefreshTokens.id] = tokenId
                it[RefreshTokens.username] = loginRequest.username
                it[RefreshTokens.tokenHash] = JwtService.hashToken(refreshToken)
                it[RefreshTokens.family] = family
                it[RefreshTokens.expiresAt] = LocalDateTime.now().plusDays(7)
            }
        }

        call.respond(HttpStatusCode.OK, buildJsonObject {
            put("accessToken", accessToken)
            put("refreshToken", refreshToken)
            put("tokenType", "Bearer")
            put("expiresIn", 900) // 15 minutes in seconds
        })
    }

    // Rotate refresh token
    post("/api/token/refresh") {
        @Serializable
        data class RefreshRequest(val refreshToken: String)

        val body = try {
            call.receive<RefreshRequest>()
        } catch (_: io.ktor.server.plugins.ContentTransformationException) {
            return@post call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Missing refreshToken"))
        }

        val tokenHash = JwtService.hashToken(body.refreshToken)

        val result = transaction {
            val existing = RefreshTokens.selectAll().where {
                (RefreshTokens.tokenHash eq tokenHash) and (RefreshTokens.revokedAt.isNull())
            }.singleOrNull() ?: return@transaction null

            // Check expiry
            if (existing[RefreshTokens.expiresAt].isBefore(LocalDateTime.now())) {
                return@transaction null
            }

            val tokenFamily = existing[RefreshTokens.family]
            val existingId = existing[RefreshTokens.id]
            val existingUsername = existing[RefreshTokens.username]

            // Revoke the used token
            RefreshTokens.update({ RefreshTokens.id eq existingId }) {
                it[RefreshTokens.revokedAt] = LocalDateTime.now()
            }

            // Issue new token in the same family
            val newRefreshToken = JwtService.generateRefreshToken()
            val newTokenId = UUID.randomUUID()

            RefreshTokens.insert {
                it[RefreshTokens.id] = newTokenId
                it[RefreshTokens.username] = existingUsername
                it[RefreshTokens.tokenHash] = JwtService.hashToken(newRefreshToken)
                it[RefreshTokens.family] = tokenFamily
                it[RefreshTokens.expiresAt] = LocalDateTime.now().plusDays(7)
            }

            // Link old token to its successor
            RefreshTokens.update({ RefreshTokens.id eq existingId }) {
                it[RefreshTokens.replacedBy] = newTokenId
            }

            Pair(existingUsername, newRefreshToken)
        }

        if (result == null) {
            return@post call.respond(HttpStatusCode.Unauthorized,
                ApiError.unauthorized("Invalid or expired refresh token"))
        }

        val (username, newRefreshToken) = result
        val refreshRole = getUserRole(username)
        val newAccessToken = JwtService.generateAccessToken(username, refreshRole)

        call.respond(HttpStatusCode.OK, buildJsonObject {
            put("accessToken", newAccessToken)
            put("refreshToken", newRefreshToken)
            put("tokenType", "Bearer")
            put("expiresIn", 900)
        })
    }

    // ── Password Reset ────────────────────────────────────────
    post("/api/password-reset") {
        val body = try {
            call.receive<PasswordResetRequest>()
        } catch (_: io.ktor.server.plugins.ContentTransformationException) {
            return@post call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Invalid request body"))
        }

        if (body.newPassword.length < 8) {
            return@post call.respond(HttpStatusCode.BadRequest,
                ApiError.badRequest("Password must be at least 8 characters"))
        }

        val resetConfig = ConfigLoader.load()
        if (body.serverSalt != resetConfig.security.serverSalt) {
            return@post call.respond(HttpStatusCode.Forbidden,
                ApiError(error = "Invalid server salt", code = "FORBIDDEN"))
        }

        // Verify current password before allowing reset
        val isCurrentPasswordValid = verifyCredentials(
            resetConfig.security.adminUsername,
            body.currentPassword,
            resetConfig,
            call.application.environment.log
        )
        if (!isCurrentPasswordValid) {
            return@post call.respond(HttpStatusCode.Unauthorized,
                ApiError.unauthorized("Current password is incorrect"))
        }

        val hashedPassword = org.mindrot.jbcrypt.BCrypt.hashpw(
            body.newPassword,
            org.mindrot.jbcrypt.BCrypt.gensalt(12)
        )

        // Update password in the Users table for the admin user
        transaction {
            se.onemanstudio.db.Users.update({
                se.onemanstudio.db.Users.username eq resetConfig.security.adminUsername
            }) {
                it[se.onemanstudio.db.Users.passwordHash] = hashedPassword
            }

            // Invalidate all refresh tokens
            RefreshTokens.deleteAll()
        }

        call.application.environment.log.info("Password reset completed for admin user")

        call.respond(HttpStatusCode.OK, buildJsonObject {
            put("success", true)
            put("message", "Password updated successfully. All sessions have been invalidated.")
        })
    }

    authenticate("admin-session", "api-jwt") {
        // Me endpoint (current user info)
        get("/admin/me") {
            val session = call.sessions.get<UserSession>()
            val principal = call.principal<JWTPrincipal>()
            
            val username = session?.username ?: principal?.payload?.subject ?: "unknown"
            val role = session?.role ?: principal?.payload?.getClaim("role")?.asString() ?: "viewer"
            
            call.respond(mapOf(
                "username" to username,
                "role" to role
            ))
        }
    }
}
