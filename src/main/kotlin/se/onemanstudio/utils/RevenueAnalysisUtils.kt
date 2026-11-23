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
import se.onemanstudio.api.models.admin.CurrencyRevenue
import se.onemanstudio.api.models.admin.RevenueAttribution
import se.onemanstudio.api.models.admin.RevenueByEvent
import se.onemanstudio.api.models.admin.RevenueStats
import se.onemanstudio.db.Events
import java.net.URI
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import kotlin.math.abs
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
 * `"revenue": <number>` patterns and aggregates the values per `currency`
 * (USD when missing). Negative amounts are refunds.
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

    /** One revenue event: a signed amount (negative is a refund) in a currency. */
    private data class Amount(val value: Double, val currency: String)

    private const val DEFAULT_CURRENCY = "USD"
    private const val MAX_CURRENCY_LENGTH = 8

    /** Largest amount, either way, that counts; anything beyond it is a typo or an attack on the totals. */
    private const val MAX_AMOUNT = 1_000_000_000.0

    /**
     * The amount of an event: the top-level `revenue` of its properties, as a number or a numeric string,
     * with its `currency` (upper-cased; [DEFAULT_CURRENCY] when missing or blank). A negative amount is a refund.
     * A `revenue` nested deeper in the properties does not count, and neither does one beyond [MAX_AMOUNT].
     */
    private fun extractRevenue(properties: String?): Amount? {
        if (properties.isNullOrBlank()) return null
        val obj = try {
            Json.parseToJsonElement(properties) as? JsonObject
        } catch (_: SerializationException) { null } ?: return null
        val value = (obj["revenue"] as? JsonPrimitive)?.content?.toDoubleOrNull()
            ?.takeIf { it.isFinite() && abs(it) <= MAX_AMOUNT } ?: return null
        val currency = (obj["currency"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.uppercase()
            ?.takeIf { it.isNotEmpty() && it.length <= MAX_CURRENCY_LENGTH } ?: DEFAULT_CURRENCY
        return Amount(value, currency)
    }

    /** Rounds an amount to cents. */
    private fun cents(amount: Double): Double = Math.round(amount * 100.0) / 100.0

    private fun List<Amount>.net() = sumOf { it.value }
    private fun List<Amount>.refunded() = -filter { it.value < 0 }.sumOf { it.value }
    private fun List<Amount>.sales() = filter { it.value >= 0 }
    private fun List<Amount>.salesAverage(): Double = sales().let { if (it.isEmpty()) 0.0 else it.sumOf { a -> a.value } / it.size }

    /**
     * Calculate aggregate revenue stats for a project in a time period, one entry per currency.
     */
    fun calculateRevenue(projectId: UUID, start: LocalDateTime, end: LocalDateTime): RevenueStats {
        return transaction {
            fun amountsBetween(from: LocalDateTime, to: LocalDateTime) = Events.selectAll().where {
                (Events.projectId eq projectId) and
                (Events.timestamp greaterEq from) and
                (Events.timestamp lessEq to) and
                (Events.eventType eq "custom") and
                Events.properties.isNotNull()
            }.mapNotNull { row -> extractRevenue(row[Events.properties]) }.groupBy { it.currency }

            val current = amountsBetween(start, end)

            // Unique visitors in period for revenue-per-visitor
            val uniqueVisitors = Events.selectAll().where {
                (Events.projectId eq projectId) and
                (Events.timestamp greaterEq start) and
                (Events.timestamp lessEq end) and
                (Events.eventType eq "pageview")
            }.map { it[Events.visitorHash] }.toSet().size.toLong()

            // Previous period for comparison
            val previous = amountsBetween(start.minus(Duration.between(start, end)), start)

            val currencies = (current.keys + previous.keys).map { currency ->
                val amounts = current[currency].orEmpty()
                val prev = previous[currency].orEmpty()
                val net = amounts.net()
                CurrencyRevenue(
                    currency = currency,
                    totalRevenue = cents(net),
                    refunds = cents(amounts.refunded()),
                    transactions = amounts.sales().size.toLong(),
                    refundCount = (amounts.size - amounts.sales().size).toLong(),
                    averageOrderValue = cents(amounts.salesAverage()),
                    revenuePerVisitor = if (uniqueVisitors > 0) cents(net / uniqueVisitors) else 0.0,
                    previousRevenue = cents(prev.net()),
                    previousTransactions = prev.sales().size.toLong()
                )
            }
            RevenueStats(currencies.sortedWith(compareByDescending<CurrencyRevenue> { it.transactions }.thenBy { it.currency }))
        }
    }

    /**
     * Revenue grouped by event name and currency (e.g. purchase, subscription, upgrade).
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
                val amount = extractRevenue(row[Events.properties]) ?: return@mapNotNull null
                (row[Events.eventName] ?: "unknown") to amount
            }.groupBy { (name, amount) -> name to amount.currency }
                .map { (key, entries) ->
                    val amounts = entries.map { it.second }
                    RevenueByEvent(
                        eventName = key.first,
                        currency = key.second,
                        revenue = cents(amounts.net()),
                        refunds = cents(amounts.refunded()),
                        transactions = amounts.sales().size.toLong(),
                        avgValue = cents(amounts.salesAverage())
                    )
                }
                .sortedByDescending { it.revenue }
                .take(20)
        }
    }

    /**
     * Revenue attribution by referrer source and UTM campaign, one row per source and currency.
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
                val amount = extractRevenue(row[Events.properties]) ?: return@mapNotNull null
                row[Events.sessionId] to amount
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

            // Map revenue events to a source and currency; a session with several purchases counts once as a converting session
            val eventsBySource = revenueEvents.groupBy { (sessionId, amount) ->
                (sessionSources[sessionId] ?: "Direct") to amount.currency
            }

            eventsBySource.map { (key, events) ->
                val (source, currency) = key
                val amounts = events.map { it.second }
                val buyingSessions = events.filter { it.second.value >= 0 }.map { it.first }.toSet().size
                val totalSessions = totalSessionsBySource[source] ?: 1
                RevenueAttribution(
                    source = source,
                    currency = currency,
                    revenue = cents(amounts.net()),
                    refunds = cents(amounts.refunded()),
                    transactions = amounts.sales().size.toLong(),
                    avgValue = cents(amounts.salesAverage()),
                    conversionRate = cents(buyingSessions.toDouble() / totalSessions * 100.0)
                )
            }.sortedByDescending { it.revenue }
                .take(20)
        }
    }
}
