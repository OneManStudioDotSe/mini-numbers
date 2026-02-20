package se.onemanstudio.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object ConversionGoals : Table("conversion_goals") {
    val id = uuid("id")
    val projectId = uuid("project_id").references(Projects.id)
    val name = varchar("name", 100)
    val goalType = varchar("goal_type", 20) // "url" or "event"
    val matchValue = varchar("match_value", 512)
    val isActive = bool("is_active").default(true)
    val createdAt = datetime("created_at").default(LocalDateTime.now())

    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_goals_project", false, projectId)
    }
}
