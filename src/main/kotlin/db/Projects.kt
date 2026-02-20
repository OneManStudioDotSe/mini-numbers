package se.onemanstudio.db

import org.jetbrains.exposed.sql.Table

object Projects : Table("projects") {
    val id = uuid("id")
    val name = varchar("name", 100)
    val domain = varchar("domain", 255) // e.g., "mysite.com"
    val apiKey = varchar("api_key", 64).uniqueIndex() // The key in the JS script

    override val primaryKey = PrimaryKey(id)
}
