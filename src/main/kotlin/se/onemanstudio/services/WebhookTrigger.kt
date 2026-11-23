package se.onemanstudio.services

import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import se.onemanstudio.db.ConversionGoals
import se.onemanstudio.db.Events
import se.onemanstudio.db.Webhooks
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit
import se.onemanstudio.utils.nowApiString

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

    /** goalId:sessionId pairs that already fired, so a goal converts once per session like the stats count it */
    private val firedConversions = Caffeine.newBuilder()
        .maximumSize(50_000)
        .expireAfterWrite(30, TimeUnit.MINUTES)
        .build<String, Boolean>()

    private val spikes = TrafficSpikeDetector()

    data class WebhookInfo(
        val id: UUID,
        val url: String,
        val secret: String,
        val events: List<String>,
        val isActive: Boolean
    )

    /**
     * Check incoming event against active webhooks and fire matching ones.
     * Called after successful event insertion in /collect.
     */
    fun checkAndFire(
        projectId: UUID,
        eventType: String,
        eventName: String?,
        path: String?,
        sessionId: String
    ) {
        val webhooks = getActiveWebhooks(projectId)
        if (webhooks.isEmpty()) return

        // Check goal conversions
        if (eventType == "custom" || eventType == "pageview") {
            checkGoalConversions(projectId, webhooks, eventType, eventName, path, sessionId)
        }

        // Only page views count as visits for spike detection (not heartbeats or custom events)
        if (eventType == "pageview") checkSpike(projectId, webhooks)
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

    /** True the first time this session converts on this goal; false for every repeat within the window. */
    internal fun firstConversionInSession(goalId: UUID, sessionId: String): Boolean {
        val key = "$goalId:$sessionId"
        if (firedConversions.getIfPresent(key) != null) return false
        firedConversions.put(key, true)
        return true
    }

    @Suppress("LongParameterList")
    private fun checkGoalConversions(
        projectId: UUID,
        webhooks: List<WebhookInfo>,
        eventType: String,
        eventName: String?,
        path: String?,
        sessionId: String
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
            if (!firstConversionInSession(goal[ConversionGoals.id], sessionId)) continue
            val payload = buildJsonObject {
                put("event", "goal_conversion")
                put("projectId", projectId.toString())
                put("goalId", goal[ConversionGoals.id].toString())
                put("goalName", goal[ConversionGoals.name])
                put("goalType", goal[ConversionGoals.goalType])
                put("matchValue", goal[ConversionGoals.matchValue])
                put("timestamp", nowApiString())
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

    private fun checkSpike(projectId: UUID, webhooks: List<WebhookInfo>) {
        val spikeWebhooks = webhooks.filter { "traffic_spike" in it.events }
        if (spikeWebhooks.isEmpty()) return

        val spike = spikes.record(projectId.toString()) ?: return
        val windowMinutes = TrafficSpikeDetector.WINDOW_MINUTES
        val payload = buildJsonObject {
            put("event", "traffic_spike")
            put("projectId", projectId.toString())
            put("currentRate", spike.windowVisits / windowMinutes.toDouble())
            put("baselineRate", spike.baselinePerWindow / windowMinutes)
            put("eventsInWindow", spike.windowVisits)
            put("windowMinutes", windowMinutes)
            put("timestamp", nowApiString())
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
        logger.info(
            "Fired traffic_spike webhook for project $projectId (${spike.windowVisits} page views in " +
                "$windowMinutes min, usual ${"%.1f".format(spike.baselinePerWindow)})"
        )
    }

    /** Invalidate cached webhooks for a project (call when webhooks are created/deleted) */
    fun invalidateProject(projectId: String) {
        webhookCache.invalidate(projectId)
    }
}
