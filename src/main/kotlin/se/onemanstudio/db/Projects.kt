package se.onemanstudio.db

import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*

object Projects : Table("projects") {
    val id = javaUUID("id")
    val name = varchar("name", 100)
    val domain = varchar("domain", 255) // e.g., "mysite.com"
    val apiKey = varchar("api_key", 64).uniqueIndex() // The key in the JS script

    override val primaryKey = PrimaryKey(id)
}
