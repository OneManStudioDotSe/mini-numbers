package se.onemanstudio.config.models

/**
 * Database configuration supporting both SQLite and PostgreSQL
 */
data class DatabaseConfig(
    val type: DatabaseType,
    val path: String? = null,           // For SQLite
    val host: String? = null,           // For PostgreSQL
    val port: Int? = null,              // For PostgreSQL
    val name: String? = null,           // For PostgreSQL
    val username: String? = null,       // For PostgreSQL
    val password: String? = null,       // For PostgreSQL
    val maxPoolSize: Int = 3            // For PostgreSQL connection pooling
)

/**
 * Database type enumeration
 */
enum class DatabaseType {
    SQLITE,
    POSTGRESQL
}
