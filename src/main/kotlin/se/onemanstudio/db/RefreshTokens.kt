package se.onemanstudio.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

/**
 * Stores refresh tokens for JWT authentication.
 * Uses token families to detect replay attacks during rotation.
 */
object RefreshTokens : Table("refresh_tokens") {
    val id = uuid("id")
    val username = varchar("username", 100)
    val tokenHash = varchar("token_hash", 64)      // SHA-256 hash (never store raw tokens)
    val family = varchar("family", 64)              // Token family for rotation detection
    val expiresAt = datetime("expires_at")
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }
    val revokedAt = datetime("revoked_at").nullable()
    val replacedBy = uuid("replaced_by").nullable() // Points to successor token in rotation chain

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("idx_refresh_tokens_hash", tokenHash)
        index("idx_refresh_tokens_family", false, family)
        index("idx_refresh_tokens_username", false, username)
    }
}
