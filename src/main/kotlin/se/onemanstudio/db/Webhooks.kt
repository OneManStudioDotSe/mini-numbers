package se.onemanstudio.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

/**
 * Webhook configurations for outbound event notifications.
 * Payloads are signed with HMAC-SHA256 using a per-webhook secret.
 */
object Webhooks : Table("webhooks") {
    val id = uuid("id")
    val projectId = uuid("project_id").references(Projects.id)
    val url = varchar("url", 1024)
    val secret = varchar("secret", 128)             // HMAC-SHA256 signing secret
    val events = varchar("events", 500)             // Comma-separated: "goal_conversion,traffic_spike"
    val isActive = bool("is_active").default(true)
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }

    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_webhooks_project", false, projectId)
    }
}

/**
 * Delivery log for webhook attempts (success, failure, retries).
 */
object WebhookDeliveries : Table("webhook_deliveries") {
    val id = uuid("id")
    val webhookId = uuid("webhook_id").references(Webhooks.id)
    val eventType = varchar("event_type", 50)
    val payload = text("payload")
    val responseCode = integer("response_code").nullable()
    val responseBody = text("response_body").nullable()
    val attempt = integer("attempt").default(1)
    val status = varchar("status", 20).default("pending")   // pending, success, failed
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }
    val deliveredAt = datetime("delivered_at").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_deliveries_webhook", false, webhookId)
        index("idx_deliveries_status", false, status)
    }
}
