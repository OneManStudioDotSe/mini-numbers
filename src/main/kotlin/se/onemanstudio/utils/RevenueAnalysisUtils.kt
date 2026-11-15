package se.onemanstudio.utils

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import se.onemanstudio.utils.RevenueAnalysisUtils.calculateRevenue
import se.onemanstudio.utils.RevenueAnalysisUtils.calculateRevenueAttribution
import se.onemanstudio.utils.RevenueAnalysisUtils.calculateRevenueByEvent
import se.onemanstudio.api.models.admin.RevenueAttribution
import se.onemanstudio.api.models.admin.RevenueByEvent
import se.onemanstudio.api.models.admin.RevenueStats
import se.onemanstudio.db.Events
import java.net.URI
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import kotlin.collections.iterator

/**
 * Revenue analytics engine.
 *
 * Revenue is extracted from custom event properties JSON. On the client the
 * developer calls:
 * ```js
 * MiniNumbers.track("purchase", { revenue: 29.99, currency: "USD" })
 * ```
 * The tracker serializes the second argument to a JSON string which is
 * stored in [Events.properties]. This object scans those JSON strings for
 * `"revenue": <number>` patterns and aggregates the values.
 *
 * ## Functions
 * - [calculateRevenue] — totals, transaction count, AOV, revenue-per-visitor,
 *   and previous-period comparison.
 * - [calculateRevenueByEvent] — revenue grouped by event name (e.g.
 *   "purchase" vs "subscription" vs "upgrade").
 * - [calculateRevenueAttribution] — which referrer / UTM source drove the
 *   most revenue, with per-source conversion rates.
 */
object RevenueAnalysisUtils {

    /**
     * Resolve the traffic source label for a session from its first pageview.
     * Priority: UTM campaign → UTM source → referrer domain → "Direct".
     */
    private fun resolveSessionSource(rows: List<org.jetbrains.exposed.v1.core.ResultRow>): String {
        val first = rows.minByOrNull { it[Events.timestamp] }
        val ref = first?.get(Events.referrer)
        val utmSource = first?.get(Events.utmSource)
        val utmCampaign = first?.get(Events.utmCampaign)
        return when {
            !utmCampaign.isNullOrBlank() -> "utm:$utmCampaign"
            !utmSource.isNullOrBlank() -> "utm:$utmSource"
            !ref.isNullOrBlank() -> {
                try {
                    URI(ref).host?.removePrefix("www.") ?: ref
                } catch (_: java.net.URISyntaxException) { ref }
            }
            else -> "Direct"
        }
    }

    /**
     * The amount of an event: the top-level `revenue` of its properties, as a number or a numeric string.
     * Negative amounts are not counted, and neither is a `revenue` nested deeper in the properties.
     */
    private fun extractRevenue(properties: String?): Double? {
        if (properties.isNullOrBlank()) return null
        val revenue = try {
            (Json.parseToJsonElement(properties) as? JsonObject)?.get("revenue") as? JsonPrimitive
        } catch (_: SerializationException) { null }
        return revenue?.content?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
    }

    /** Rounds an amount to cents. */
    private fun cents(amount: Double): Double = Math.round(amount * 100.0) / 100.0

    /**
     * Calculate aggregate revenue stats for a project in a time period.
     */
    fun calculateRevenue(projectId: UUID, start: LocalDateTime, end: LocalDateTime): RevenueStats {
        return transaction {
            val events = Events.selectAll().where {
                (Events.projectId eq projectId) and
                (Events.timestamp greaterEq start) and
                (Events.timestamp lessEq end) and
                (Events.eventType eq "custom") and
                Events.properties.isNotNull()
            }.mapNotNull { row ->
                extractRevenue(row[Events.properties])
            }

            val totalRevenue = events.sum()
            val transactions = events.size.toLong()
            val aov = if (transactions > 0) totalRevenue / transactions else 0.0

            // Unique visitors in period for revenue-per-visitor
            val uniqueVisitors = Events.selectAll().where {
                (Events.projectId eq projectId) and
                (Events.timestamp greaterEq start) and
                (Events.timestamp lessEq end) and
                (Events.eventType eq "pageview")
            }.map { it[Events.visitorHash] }.toSet().size.toLong()

            val rpv = if (uniqueVisitors > 0) totalRevenue / uniqueVisitors else 0.0

            // Previous period for comparison
            val duration = Duration.between(start, end)
            val prevEnd = start
            val prevStart = prevEnd.minus(duration)

            val prevEvents = Events.selectAll().where {
                (Events.projectId eq projectId) and
                (Events.timestamp greaterEq prevStart) and
                (Events.timestamp lessEq prevEnd) and
                (Events.eventType eq "custom") and
                Events.properties.isNotNull()
            }.mapNotNull { row ->
                extractRevenue(row[Events.properties])
            }

            RevenueStats(
                totalRevenue = cents(totalRevenue),
                transactions = transactions,
                averageOrderValue = cents(aov),
                revenuePerVisitor = cents(rpv),
                previousRevenue = cents(prevEvents.sum()),
                previousTransactions = prevEvents.size.toLong()
            )
        }
    }

    /**
     * Revenue grouped by event name (e.g. purchase, subscription, upgrade).
     */
    fun calculateRevenueByEvent(projectId: UUID, start: LocalDateTime, end: LocalDateTime): List<RevenueByEvent> {
        return transaction {
            val rows = Events.selectAll().where {
                (Events.projectId eq projectId) and
                (Events.timestamp greaterEq start) and
                (Events.timestamp lessEq end) and
                (Events.eventType eq "custom") and
                Events.properties.isNotNull() and
                Events.eventName.isNotNull()
            }.toList()

            rows.mapNotNull { row ->
                val revenue = extractRevenue(row[Events.properties]) ?: return@mapNotNull null
                Triple(row[Events.eventName] ?: "unknown", revenue, 1)
            }.groupBy { it.first }
                .map { (name, entries) ->
                    val totalRev = entries.sumOf { it.second }
                    val count = entries.size.toLong()
                    RevenueByEvent(
                        eventName = name,
                        revenue = cents(totalRev),
                        transactions = count,
                        avgValue = cents(totalRev / count)
                    )
                }
                .sortedByDescending { it.revenue }
                .take(20)
        }
    }

    /**
     * Revenue attribution by referrer source and UTM campaign.
     * For each source, shows how much revenue visitors from that source generated.
     */
    fun calculateRevenueAttribution(projectId: UUID, start: LocalDateTime, end: LocalDateTime): List<RevenueAttribution> {
        return transaction {
            // Get all revenue events with their session IDs
            val revenueEvents = Events.selectAll().where {
                (Events.projectId eq projectId) and
                (Events.timestamp greaterEq start) and
                (Events.timestamp lessEq end) and
                (Events.eventType eq "custom") and
                Events.properties.isNotNull()
            }.mapNotNull { row ->
                val rev = extractRevenue(row[Events.properties]) ?: return@mapNotNull null
                row[Events.sessionId] to rev
            }

            if (revenueEvents.isEmpty()) return@transaction emptyList()

            val revenueSessions = revenueEvents.map { it.first }.toSet()

            // Get referrer for each revenue-generating session (from the first pageview)
            val sessionSources = Events.selectAll().where {
                (Events.projectId eq projectId) and
                (Events.timestamp greaterEq start) and
                (Events.timestamp lessEq end) and
                (Events.eventType eq "pageview") and
                (Events.sessionId inList revenueSessions)
            }.toList()
                .groupBy { it[Events.sessionId] }
                .mapValues { (_, rows) -> resolveSessionSource(rows) }

            // Total sessions in period for conversion rate
            val totalSessionsBySource = Events.selectAll().where {
                (Events.projectId eq projectId) and
                (Events.timestamp greaterEq start) and
                (Events.timestamp lessEq end) and
                (Events.eventType eq "pageview")
            }.toList()
                .groupBy { it[Events.sessionId] }
                .mapValues { (_, rows) -> resolveSessionSource(rows) }
                .values.groupBy { it }.mapValues { it.value.size }

            // Map revenue events to sources; a session with several purchases counts once as a converting session
            val eventsBySource = revenueEvents.groupBy { (sessionId, _) -> sessionSources[sessionId] ?: "Direct" }

            eventsBySource.map { (source, events) ->
                val totalRev = events.sumOf { it.second }
                val txns = events.size.toLong()
                val buyingSessions = events.map { it.first }.toSet().size
                val totalSessions = totalSessionsBySource[source] ?: 1
                RevenueAttribution(
                    source = source,
                    revenue = cents(totalRev),
                    transactions = txns,
                    avgValue = cents(totalRev / txns),
                    conversionRate = cents(buyingSessions.toDouble() / totalSessions * 100.0)
                )
            }.sortedByDescending { it.revenue }
                .take(20)
        }
    }
}
