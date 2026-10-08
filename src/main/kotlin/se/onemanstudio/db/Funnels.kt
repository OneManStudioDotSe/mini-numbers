package se.onemanstudio.db

import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.javatime.datetime
import java.time.LocalDateTime

object Funnels : Table("funnels") {
    val id = javaUUID("id")
    val projectId = javaUUID("project_id").references(Projects.id)
    val name = varchar("name", 100)
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }

    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_funnels_project", false, projectId)
    }
}
