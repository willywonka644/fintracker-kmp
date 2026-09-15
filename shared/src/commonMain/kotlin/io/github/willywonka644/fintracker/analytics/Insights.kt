package io.github.willywonka644.fintracker.analytics

import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.analytics.excludeTransfers
import kotlin.math.abs

data class Insights(
    val income: Double,
    val expenses: Double, // positive number (absolute spend)
    val net: Double,
    val topCategory: String?
)

fun calculateInsights(bookings: List<Booking>): Insights {
    val settled = bookings.excludeTransfers()
    val income = settled.filter { it.amount > 0 }.sumOf { it.amount }
    val expensesAbs = settled.filter { it.amount < 0 }.sumOf { abs(it.amount) }
    val net = income - expensesAbs

    val topCategory = settled
        .filter { it.amount < 0 }
        .groupBy { it.category?.takeIf { c -> c.isNotBlank() } ?: "Uncategorized" }
        .mapValues { (_, list) -> list.sumOf { abs(it.amount) } }
        .maxByOrNull { it.value }
        ?.key

    return Insights(
        income = income,
        expenses = expensesAbs,
        net = net,
        topCategory = topCategory
    )
}
