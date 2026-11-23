package se.onemanstudio.api.models.admin

import kotlinx.serialization.Serializable

/**
 * Revenue figures of one currency. Amounts in different currencies are never added together.
 * `totalRevenue` is net: sales minus refunds (a refund is an event with a negative `revenue`).
 * `refunds` is the refunded sum as a positive number; `transactions` counts sales only.
 */
@Serializable
data class CurrencyRevenue(
    val currency: String,
    val totalRevenue: Double,
    val refunds: Double = 0.0,
    val transactions: Long,
    val refundCount: Long = 0,
    val averageOrderValue: Double,
    val revenuePerVisitor: Double,
    val previousRevenue: Double = 0.0,
    val previousTransactions: Long = 0
)

@Serializable
data class RevenueStats(
    val currencies: List<CurrencyRevenue>
)

@Serializable
data class RevenueByEvent(
    val eventName: String,
    val currency: String,
    val revenue: Double,
    val refunds: Double = 0.0,
    val transactions: Long,
    val avgValue: Double
)

@Serializable
data class RevenueAttribution(
    val source: String,
    val currency: String,
    val revenue: Double,
    val refunds: Double = 0.0,
    val transactions: Long,
    val avgValue: Double,
    val conversionRate: Double
)
