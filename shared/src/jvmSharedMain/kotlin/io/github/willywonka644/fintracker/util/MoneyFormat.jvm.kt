package io.github.willywonka644.fintracker.util

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

private val currencyFormatter: NumberFormat by lazy {
    NumberFormat.getCurrencyInstance(Locale.GERMANY)
}

actual fun formatMoney(amount: Double): String = currencyFormatter.format(amount)

actual fun formatMoneyShort(amount: Double): String {
    val absVal = abs(amount)
    val sign = if (amount < -MoneyFormat.EPS) "-" else ""
    return when {
        absVal >= 1_000_000 -> "${sign}${String.format(Locale.GERMANY, "%.1f", absVal / 1_000_000)}M €"
        absVal >= 10_000    -> "${sign}${String.format(Locale.GERMANY, "%.0f", absVal / 1_000)}k €"
        absVal >= 1_000     -> "${sign}${String.format(Locale.GERMANY, "%.1f", absVal / 1_000)}k €"
        absVal < MoneyFormat.EPS -> "0 €"
        else                -> "${sign}${String.format(Locale.GERMANY, "%.0f", absVal)} €"
    }
}
