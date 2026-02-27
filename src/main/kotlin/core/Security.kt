package se.onemanstudio.core

import com.auth0.jwt.JWT
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import se.onemanstudio.api.models.ApiError
import org.mindrot.jbcrypt.BCrypt
import se.onemanstudio.config.models.AppConfig
import se.onemanstudio.core.models.LoginAttempt
import se.onemanstudio.core.models.UserSession
import se.onemanstudio.db.Users
import java.time.Duration


/**
 * Verify username and password against configuration
 * @param username Username to verify
 * @param password Plain text password
 * @param config Application configuration
 * @param logger Logger for security events
 * @return True if credentials are valid
 */
fun verifyCredentials(username: String, password: String, config: AppConfig, logger: org.slf4j.Logger): Boolean {
    // Cache for tracking login attempts by username. Expires after 15 minutes of inactivity
    val loginAttempts: Cache<String, LoginAttempt> = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofMinutes(15))
        .maximumSize(1000)
        .build()

    // Check if username is locked out
    val attempt = loginAttempts.get(username) { LoginAttempt() }!!
    val now = System.currentTimeMillis()
    val lockoutUntil = attempt.lockoutUntil.get()

    if (lockoutUntil > now) {
        val remainingSeconds = (lockoutUntil - now) / 1000
        logger.warn("Login attempt for locked-out username: $username (${remainingSeconds}s remaining)")
        return false
    }

    // Try database-backed authentication first (RBAC-aware)
    val dbUser = try {
        transaction {
            Users.selectAll().where {
                (Users.username eq username) and (Users.isActive eq true)
            }.singleOrNull()
        }
    } catch (e: Exception) {
        null // DB not available yet (setup mode), fall back to .env config
    }

    val passwordMatches: Boolean
    if (dbUser != null) {
        // DB-backed user found
        val storedHash = dbUser[Users.passwordHash]
        passwordMatches = try {
            BCrypt.checkpw(password, storedHash)
        } catch (e: Exception) {
            logger.error("Password verification error: ${e.message}")
            false
        }
    } else {
        // Fall back to .env config (backward compat / setup mode)
        if (username != config.security.adminUsername) {
            recordFailedAttempt(username, attempt, logger)
            logger.warn("Failed authentication - unknown username")
            return false
        }

        passwordMatches = try {
            if (config.security.adminPassword.startsWith("$2a$") ||
                config.security.adminPassword.startsWith("$2b$")) {
                BCrypt.checkpw(password, config.security.adminPassword)
            } else {
                logger.error("Admin password is not BCrypt-hashed. Please re-run the setup wizard to fix this.")
                false
            }
        } catch (e: Exception) {
            logger.error("Password verification error: ${e.message}")
            false
        }
    }

    if (passwordMatches) {
        attempt.failedAttempts.set(0)
        attempt.lockoutUntil.set(0)
        logger.info("Successful authentication for user: $username")
        return true
    } else {
        recordFailedAttempt(username, attempt, logger)
        logger.warn("Failed authentication - invalid password for user: $username")
        return false
    }
}

/**
 * Look up a user's role from the database.
 * Returns "admin" if the user is not found in the DB (backward compat with .env-only auth).
 */
fun getUserRole(username: String): String {
    return try {
        transaction {
            Users.selectAll().where {
                Users.username eq username
            }.singleOrNull()?.get(Users.role) ?: "admin"
        }
    } catch (e: Exception) {
        "admin" // DB not available, default to admin for .env-based auth
    }
}

/**
 * Record a failed login attempt and apply lockout if threshold exceeded
 * Threshold: 5 failed attempts = 15 minute lockout
 */
fun recordFailedAttempt(identifier: String, attempt: LoginAttempt, logger: org.slf4j.Logger) {
    val failedCount = attempt.failedAttempts.incrementAndGet()

    if (failedCount >= 5) {
        // Lock out for 15 minutes
        val lockoutUntil = System.currentTimeMillis() + (15 * 60 * 1000)
        attempt.lockoutUntil.set(lockoutUntil)
        logger.warn("SECURITY ALERT: Account '$identifier' locked out for 15 minutes after $failedCount failed login attempts")
    } else {
        logger.info("Failed login attempt #$failedCount for: $identifier")
    }
}

/**
 * Configure session-based authentication for the admin panel
 * Uses cookie sessions instead of HTTP Basic Auth for better UX
 */
fun Application.configureSecurity(config: AppConfig) {
    install(Sessions) {
        cookie<UserSession>("mini_numbers_session") {
            cookie.path = "/"
            cookie.maxAgeInSeconds = 60 * 60 * 24 * 7 // 7 days
            cookie.httpOnly = true
            cookie.secure = !config.server.isDevelopment // Secure in production (requires HTTPS)
            cookie.extensions["SameSite"] = "Strict"    // Prevent CSRF
        }
    }

    install(Authentication) {
        session<UserSession>("admin-session") {
            validate { session ->
                // Reject sessions older than 4 hours (inactivity timeout)
                val maxSessionAge = 4 * 60 * 60 * 1000L
                if (System.currentTimeMillis() - session.createdAt > maxSessionAge) {
                    null
                } else {
                    session
                }
            }
            challenge {
                // Return 401 JSON so frontend fetch() calls get a proper error
                // instead of being redirected to an HTML login page
                call.respond(HttpStatusCode.Unauthorized,
                    ApiError.unauthorized("Authentication required"))
            }
        }

        // JWT authentication for programmatic API access
        jwt("api-jwt") {
            realm = "Mini Numbers API"
            verifier(
                try {
                    JwtService.getVerifier()
                } catch (e: UninitializedPropertyAccessException) {
                    // JWT not yet initialized (setup mode) — create a dummy verifier
                    JWT.require(com.auth0.jwt.algorithms.Algorithm.HMAC256("not-initialized")).build()
                }
            )
            validate { credential ->
                if (credential.payload.subject != null) {
                    JWTPrincipal(credential.payload)
                } else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized,
                    ApiError.unauthorized("Invalid or expired token"))
            }
        }
    }
}
