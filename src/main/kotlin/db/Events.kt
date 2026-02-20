package se.onemanstudio.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object Events : Table("events") {
    val id = long("id").autoIncrement()
    val projectId = uuid("project_id").references(Projects.id)
    val visitorHash = varchar("visitor_hash", 64)
    val sessionId = varchar("session_id", 64)
    val eventType = varchar("event_type", 20)
    val path = varchar("path", 512)
    val referrer = varchar("referrer", 512).nullable()
    val country = varchar("country", 100).nullable()
    val city = varchar("city", 100).nullable()
    val duration = integer("duration").default(0) // Seconds spent
    val timestamp = datetime("timestamp").default(LocalDateTime.now())
    val browser = varchar("browser", 50).nullable()
    val os = varchar("os", 50).nullable()
    val device = varchar("device", 50).nullable()

    override val primaryKey = PrimaryKey(id)

    // Performance indexes for time-based and project-specific queries
    init {
        index("idx_events_timestamp", false, timestamp)
        index("idx_events_project_timestamp", false, projectId, timestamp)
        index("idx_events_project_session", false, projectId, sessionId)
    }
}
