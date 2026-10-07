package se.onemanstudio.db

import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*

object FunnelSteps : Table("funnel_steps") {
    val id = javaUUID("id")
    val funnelId = javaUUID("funnel_id").references(Funnels.id)
    val stepNumber = integer("step_number")
    val name = varchar("name", 100)
    val stepType = varchar("step_type", 20) // "url" or "event"
    val matchValue = varchar("match_value", 512)

    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_steps_funnel", false, funnelId)
    }
}
