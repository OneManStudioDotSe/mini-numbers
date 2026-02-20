package se.onemanstudio.core

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import org.mindrot.jbcrypt.BCrypt
import se.onemanstudio.config.models.AppConfig
import se.onemanstudio.core.models.LoginAttempt
import se.onemanstudio.core.models.UserSession
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

    // Check username matches
    if (username != config.security.adminUsername) {
        recordFailedAttempt(username, attempt, logger)
        logger.warn("Failed authentication - invalid username: $username")
        return false
    }

    // Verify password using BCrypt
    val passwordMatches = try {
        if (config.security.adminPassword.startsWith("$2a$") ||
            config.security.adminPassword.startsWith("$2b$")) {
            // Stored password is hashed - verify with BCrypt
            BCrypt.checkpw(password, config.security.adminPassword)
        } else {
            // Legacy: plain text password (for backward compatibility during migration)
            logger.warn("SECURITY WARNING: Admin password is not hashed! Please run setup wizard to re-hash password.")
            password == config.security.adminPassword
        }
    } catch (e: Exception) {
        logger.error("Password verification error: ${e.message}")
        false
    }

    if (passwordMatches) {
        // Success - reset failed attempts
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
 * Record a failed login attempt and apply lockout if threshold exceeded
 * Threshold: 5 failed attempts = 15 minute lockout
 */
fun recordFailedAttempt(identifier: String, attempt: LoginAttempt, logger: org.slf4j.Logger) {
    val failedCount = attempt.failedAttempts.incrementAndGet()

    if (failedCount >= 5) {
        // Lock out for 15 minutes
        val lockoutUntil = System.currentTimeMillis() + (15 * 60 * 1000)
        attempt.lockoutUntil.set(lockoutUntil)
        logger.warn("⚠️ SECURITY ALERT: Account '$identifier' locked out for 15 minutes after $failedCount failed login attempts")
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
            cookie.secure = false // Set to true in production with HTTPS
        }
    }

    install(Authentication) {
        session<UserSession>("admin-session") {
            validate { session ->
                // Session is valid if it exists
                session
            }
            challenge {
                // Redirect to login page instead of showing HTTP auth dialog
                call.respondRedirect("/login")
            }
        }
    }
}
