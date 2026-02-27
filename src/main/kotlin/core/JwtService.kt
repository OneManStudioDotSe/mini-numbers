package se.onemanstudio.core

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.JWTVerifier
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.*

/**
 * JWT service for programmatic API access.
 * Derives the signing key from the server salt so no additional secrets are needed.
 * Access tokens are short-lived (15 min); refresh tokens handle long sessions.
 */
object JwtService {
    private lateinit var algorithm: Algorithm
    private lateinit var verifier: JWTVerifier
    private const val ISSUER = "mini-numbers"
    private const val ACCESS_TOKEN_MINUTES = 15L
    private val secureRandom = SecureRandom()

    fun init(serverSalt: String) {
        // Derive JWT signing key from server salt (different domain from visitor hashing)
        val jwtSecret = sha256("jwt-signing-key:$serverSalt")
        algorithm = Algorithm.HMAC256(jwtSecret)
        verifier = JWT.require(algorithm)
            .withIssuer(ISSUER)
            .build()
    }

    /**
     * Generate a short-lived access token (15 minutes).
     */
    fun generateAccessToken(username: String, role: String = "admin"): String {
        return JWT.create()
            .withIssuer(ISSUER)
            .withSubject(username)
            .withClaim("role", role)
            .withExpiresAt(Date.from(Instant.now().plusSeconds(ACCESS_TOKEN_MINUTES * 60)))
            .withJWTId(UUID.randomUUID().toString())
            .sign(algorithm)
    }

    /**
     * Generate a cryptographically random refresh token (64-char hex string).
     */
    fun generateRefreshToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun getAlgorithm(): Algorithm = algorithm
    fun getVerifier(): JWTVerifier = verifier
    fun getIssuer(): String = ISSUER

    /**
     * Hash a refresh token for storage (never store raw tokens).
     */
    fun hashToken(token: String): String = sha256(token)

    private fun sha256(input: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
