package se.onemanstudio.utils

import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
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
import kotlin.math.roundToInt

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
    private fun resolveSessionSource(rows: List<org.jetbrains.exposed.sql.ResultRow>): String {
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
     * Parse revenue value from a JSON properties string.
     * Looks for "revenue":N or "revenue":"N" patterns.
     */
    private fun extractRevenue(properties: String?): Double? {
        if (properties.isNullOrBlank()) return null
        val match = Regex(""""revenue"\s*:\s*"?(\d+(?:\.\d+)?)"?""").find(properties)
        return match?.groupValues?.get(1)?.toDoubleOrNull()
    }

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
                totalRevenue = (totalRevenue * 100.0).roundToInt() / 100.0,
                transactions = transactions,
                averageOrderValue = (aov * 100.0).roundToInt() / 100.0,
                revenuePerVisitor = (rpv * 100.0).roundToInt() / 100.0,
                previousRevenue = (prevEvents.sum() * 100.0).roundToInt() / 100.0,
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
                        revenue = (totalRev * 100.0).roundToInt() / 100.0,
                        transactions = count,
                        avgValue = ((totalRev / count) * 100.0).roundToInt() / 100.0
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

            // Map revenue to sources
            val revenueBySession = revenueEvents.groupBy { it.first }
                .mapValues { (_, entries) -> entries.sumOf { it.second } }

            val attributionMap = mutableMapOf<String, MutableList<Double>>()
            for ((sessionId, revenue) in revenueBySession) {
                val source = sessionSources[sessionId] ?: "Direct"
                attributionMap.getOrPut(source) { mutableListOf() }.add(revenue)
            }

            attributionMap.map { (source, revenues) ->
                val totalRev = revenues.sum()
                val txns = revenues.size.toLong()
                val totalSessions = totalSessionsBySource[source] ?: 1
                RevenueAttribution(
                    source = source,
                    revenue = (totalRev * 100.0).roundToInt() / 100.0,
                    transactions = txns,
                    avgValue = ((totalRev / txns) * 100.0).roundToInt() / 100.0,
                    conversionRate = ((txns.toDouble() / totalSessions) * 10000.0).roundToInt() / 100.0
                )
            }.sortedByDescending { it.revenue }
                .take(20)
        }
    }
}
