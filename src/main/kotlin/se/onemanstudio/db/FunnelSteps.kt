package se.onemanstudio.db

import org.jetbrains.exposed.sql.Table

object FunnelSteps : Table("funnel_steps") {
    val id = uuid("id")
    val funnelId = uuid("funnel_id").references(Funnels.id)
    val stepNumber = integer("step_number")
    val name = varchar("name", 100)
    val stepType = varchar("step_type", 20) // "url" or "event"
    val matchValue = varchar("match_value", 512)

    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_steps_funnel", false, funnelId)
    }
}
