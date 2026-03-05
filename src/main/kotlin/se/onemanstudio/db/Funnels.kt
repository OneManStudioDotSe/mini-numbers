package se.onemanstudio.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object Funnels : Table("funnels") {
    val id = uuid("id")
    val projectId = uuid("project_id").references(Projects.id)
    val name = varchar("name", 100)
    val createdAt = datetime("created_at").default(LocalDateTime.now())

    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_funnels_project", false, projectId)
    }
}
