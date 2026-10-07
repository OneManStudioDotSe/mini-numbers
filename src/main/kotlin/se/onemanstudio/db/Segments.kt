package se.onemanstudio.db

import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.javatime.datetime
import java.time.LocalDateTime

/**
 * User segments table for visual filter builder
 * Segments define reusable filters with AND/OR logic
 */
object Segments : Table("segments") {
    val id = javaUUID("id")
    val projectId = javaUUID("project_id").references(Projects.id)
    val name = varchar("name", 100)
    val description = varchar("description", 255).nullable()
    val filtersJson = text("filters_json") // JSON: [{field, operator, value, logic}]
    val createdAt = datetime("created_at").default(LocalDateTime.now())
    val updatedAt = datetime("updated_at").default(LocalDateTime.now())

    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_segments_project", false, projectId)
    }
}
