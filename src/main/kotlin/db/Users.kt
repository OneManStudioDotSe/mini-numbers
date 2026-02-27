package se.onemanstudio.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

/**
 * Stores user accounts for RBAC.
 * Roles: "admin" (full access) and "viewer" (read-only dashboard access).
 */
object Users : Table("users") {
    val id = uuid("id")
    val username = varchar("username", 100).uniqueIndex("idx_users_username")
    val passwordHash = varchar("password_hash", 255)
    val role = varchar("role", 20).default("viewer")
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }
    val isActive = bool("is_active").default(true)

    override val primaryKey = PrimaryKey(id)
}
