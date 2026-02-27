package se.onemanstudio

import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import se.onemanstudio.api.models.RevenueAttribution
import se.onemanstudio.api.models.RevenueByEvent
import se.onemanstudio.api.models.RevenueStats
import se.onemanstudio.db.Events
import java.time.LocalDateTime
import java.util.UUID

/**
 * Revenue analysis utilities.
 *
 * Revenue is extracted from custom event properties JSON:
 *   MiniNumbers.track("purchase", { revenue: 29.99, currency: "USD" })
 * The tracker serializes props to a JSON string stored in Events.properties.
 */
object RevenueAnalysisUtils {

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
                extractRevenue(row[Events.properties])?.let { revenue ->
                    revenue
                }
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
            val duration = java.time.Duration.between(start, end)
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
                totalRevenue = Math.round(totalRevenue * 100.0) / 100.0,
                transactions = transactions,
                averageOrderValue = Math.round(aov * 100.0) / 100.0,
                revenuePerVisitor = Math.round(rpv * 100.0) / 100.0,
                previousRevenue = Math.round(prevEvents.sum() * 100.0) / 100.0,
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
                        revenue = Math.round(totalRev * 100.0) / 100.0,
                        transactions = count,
                        avgValue = Math.round((totalRev / count) * 100.0) / 100.0
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
                .mapValues { (_, rows) ->
                    val first = rows.minByOrNull { it[Events.timestamp] }
                    val ref = first?.get(Events.referrer)
                    val utmSource = first?.get(Events.utmSource)
                    val utmCampaign = first?.get(Events.utmCampaign)
                    // Prefer UTM source/campaign, fall back to referrer
                    when {
                        !utmCampaign.isNullOrBlank() -> "utm:$utmCampaign"
                        !utmSource.isNullOrBlank() -> "utm:$utmSource"
                        !ref.isNullOrBlank() -> {
                            // Extract domain from referrer URL
                            try {
                                java.net.URI(ref).host?.removePrefix("www.") ?: ref
                            } catch (_: Exception) { ref }
                        }
                        else -> "Direct"
                    }
                }

            // Total sessions in period for conversion rate
            val totalSessionsBySource = Events.selectAll().where {
                (Events.projectId eq projectId) and
                (Events.timestamp greaterEq start) and
                (Events.timestamp lessEq end) and
                (Events.eventType eq "pageview")
            }.toList()
                .groupBy { it[Events.sessionId] }
                .mapValues { (_, rows) ->
                    val first = rows.minByOrNull { it[Events.timestamp] }
                    val ref = first?.get(Events.referrer)
                    val utmSource = first?.get(Events.utmSource)
                    val utmCampaign = first?.get(Events.utmCampaign)
                    when {
                        !utmCampaign.isNullOrBlank() -> "utm:$utmCampaign"
                        !utmSource.isNullOrBlank() -> "utm:$utmSource"
                        !ref.isNullOrBlank() -> {
                            try {
                                java.net.URI(ref).host?.removePrefix("www.") ?: ref
                            } catch (_: Exception) { ref }
                        }
                        else -> "Direct"
                    }
                }.values.groupBy { it }.mapValues { it.value.size }

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
                    revenue = Math.round(totalRev * 100.0) / 100.0,
                    transactions = txns,
                    avgValue = Math.round((totalRev / txns) * 100.0) / 100.0,
                    conversionRate = Math.round((txns.toDouble() / totalSessions) * 10000.0) / 100.0
                )
            }.sortedByDescending { it.revenue }
                .take(20)
        }
    }
}
