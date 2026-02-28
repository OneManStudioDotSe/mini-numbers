package se.onemanstudio.services

import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import se.onemanstudio.db.ConversionGoals
import se.onemanstudio.db.Events
import se.onemanstudio.db.Webhooks
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Bridges the /collect endpoint to webhook firing.
 * Checks incoming events against active webhooks and triggers delivery when criteria match.
 */
object WebhookTrigger {
    private val logger = LoggerFactory.getLogger(WebhookTrigger::class.java)

    /** Cache active webhooks per project (60s TTL) to avoid DB queries on every /collect request */
    private val webhookCache = Caffeine.newBuilder()
        .maximumSize(200)
        .expireAfterWrite(60, TimeUnit.SECONDS)
        .build<String, List<WebhookInfo>>()

    /** Sliding window event counters per project for traffic spike detection */
    private val trafficCounters = Caffeine.newBuilder()
        .maximumSize(500)
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .build<String, TrafficWindow>()

    data class WebhookInfo(
        val id: UUID,
        val url: String,
        val secret: String,
        val events: List<String>,
        val isActive: Boolean
    )

    data class TrafficWindow(
        val counter: AtomicLong = AtomicLong(0),
        val windowStart: Long = System.currentTimeMillis(),
        var baselineRate: Double = 0.0
    )

    /**
     * Check incoming event against active webhooks and fire matching ones.
     * Called after successful event insertion in /collect.
     */
    fun checkAndFire(
        projectId: UUID,
        eventType: String,
        eventName: String?,
        path: String?
    ) {
        val webhooks = getActiveWebhooks(projectId)
        if (webhooks.isEmpty()) return

        // Check goal conversions
        if (eventType == "custom" || eventType == "pageview") {
            checkGoalConversions(projectId, webhooks, eventType, eventName, path)
        }

        // Update traffic counter and check for spikes
        updateTrafficAndCheckSpike(projectId, webhooks)
    }

    private fun getActiveWebhooks(projectId: UUID): List<WebhookInfo> {
        return webhookCache.get(projectId.toString()) {
            transaction {
                Webhooks.selectAll().where {
                    (Webhooks.projectId eq projectId) and (Webhooks.isActive eq true)
                }.map {
                    WebhookInfo(
                        id = it[Webhooks.id],
                        url = it[Webhooks.url],
                        secret = it[Webhooks.secret],
                        events = it[Webhooks.events].split(",").map { e -> e.trim() },
                        isActive = it[Webhooks.isActive]
                    )
                }
            }
        }
    }

    private fun checkGoalConversions(
        projectId: UUID,
        webhooks: List<WebhookInfo>,
        eventType: String,
        eventName: String?,
        path: String?
    ) {
        val goalWebhooks = webhooks.filter { "goal_conversion" in it.events }
        if (goalWebhooks.isEmpty()) return

        // Check if this event matches any active goal
        val matchingGoals = transaction {
            ConversionGoals.selectAll().where {
                (ConversionGoals.projectId eq projectId) and (ConversionGoals.isActive eq true)
            }.toList().filter { goal ->
                val goalType = goal[ConversionGoals.goalType]
                val matchValue = goal[ConversionGoals.matchValue]
                when (goalType) {
                    "url" -> eventType == "pageview" && path == matchValue
                    "event" -> eventType == "custom" && eventName == matchValue
                    else -> false
                }
            }
        }

        for (goal in matchingGoals) {
            val payload = buildJsonObject {
                put("event", "goal_conversion")
                put("projectId", projectId.toString())
                put("goalId", goal[ConversionGoals.id].toString())
                put("goalName", goal[ConversionGoals.name])
                put("goalType", goal[ConversionGoals.goalType])
                put("matchValue", goal[ConversionGoals.matchValue])
                put("timestamp", LocalDateTime.now().toString())
                if (path != null) put("path", path)
                if (eventName != null) put("eventName", eventName)
            }.toString()

            for (webhook in goalWebhooks) {
                WebhookService.deliverAsync(
                    webhookId = webhook.id,
                    url = webhook.url,
                    secret = webhook.secret,
                    eventType = "goal_conversion",
                    payload = payload
                )
            }
            logger.debug("Fired goal_conversion webhook for goal '${goal[ConversionGoals.name]}' on project $projectId")
        }
    }

    private fun updateTrafficAndCheckSpike(projectId: UUID, webhooks: List<WebhookInfo>) {
        val spikeWebhooks = webhooks.filter { "traffic_spike" in it.events }
        if (spikeWebhooks.isEmpty()) return

        val key = projectId.toString()
        val window = trafficCounters.get(key) { TrafficWindow() }
        val count = window.counter.incrementAndGet()

        val elapsedMinutes = (System.currentTimeMillis() - window.windowStart) / 60_000.0
        if (elapsedMinutes < 1.0) return // Need at least 1 minute of data

        val currentRate = count / elapsedMinutes

        // Update baseline with exponential moving average
        if (window.baselineRate == 0.0) {
            window.baselineRate = currentRate
            return
        }
        window.baselineRate = window.baselineRate * 0.9 + currentRate * 0.1

        // Fire if current rate exceeds 2x baseline and we have meaningful traffic
        if (currentRate > window.baselineRate * 2.0 && currentRate > 10) {
            val payload = buildJsonObject {
                put("event", "traffic_spike")
                put("projectId", projectId.toString())
                put("currentRate", currentRate)
                put("baselineRate", window.baselineRate)
                put("eventsInWindow", count)
                put("timestamp", LocalDateTime.now().toString())
            }.toString()

            for (webhook in spikeWebhooks) {
                WebhookService.deliverAsync(
                    webhookId = webhook.id,
                    url = webhook.url,
                    secret = webhook.secret,
                    eventType = "traffic_spike",
                    payload = payload
                )
            }
            logger.info("Fired traffic_spike webhook for project $projectId (rate: ${"%.1f".format(currentRate)}/min, baseline: ${"%.1f".format(window.baselineRate)}/min)")

            // Reset counter after firing to avoid re-triggering
            window.counter.set(0)
            trafficCounters.put(key, TrafficWindow(baselineRate = window.baselineRate))
        }
    }

    /** Invalidate cached webhooks for a project (call when webhooks are created/deleted) */
    fun invalidateProject(projectId: String) {
        webhookCache.invalidate(projectId)
    }
}
