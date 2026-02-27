package se.onemanstudio.api.models

import kotlinx.serialization.Serializable

@Serializable
data class RevenueStats(
    val totalRevenue: Double,
    val transactions: Long,
    val averageOrderValue: Double,
    val revenuePerVisitor: Double,
    val previousRevenue: Double = 0.0,
    val previousTransactions: Long = 0
)

@Serializable
data class RevenueByEvent(
    val eventName: String,
    val revenue: Double,
    val transactions: Long,
    val avgValue: Double
)

@Serializable
data class RevenueAttribution(
    val source: String,
    val revenue: Double,
    val transactions: Long,
    val avgValue: Double,
    val conversionRate: Double
)
