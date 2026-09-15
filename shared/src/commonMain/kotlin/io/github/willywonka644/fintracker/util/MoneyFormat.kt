package io.github.willywonka644.fintracker.util

import kotlin.math.abs

/**
 * Platform-specific: format [amount] as a full currency string, e.g. "1.234,50 €".
 */
expect fun formatMoney(amount: Double): String

/**
 * Platform-specific: format [amount] in compact notation for chart axis labels, e.g. "1,2k €".
 */
expect fun formatMoneyShort(amount: Double): String

/**
 * Central money formatting helpers (Germany / EUR style).
 * Keep formatting logic in one place to avoid copy/paste all over the UI.
 */
object MoneyFormat {

    // Treat very small values as 0 to avoid "+ 0,00 €" because of floating point artifacts
    internal const val EPS = 0.0000001

    /** 1234.5 -> "1.234,50 €" */
    fun currency(amount: Double): String = formatMoney(amount)

    /** -12.3 -> "12,30 €" (absolute value, no sign) */
    fun currencyAbs(amount: Double): String = formatMoney(abs(amount))

    /**
     * Compact formatting for chart axis labels.
     * Uses shorter notation: "1.234 €" (no decimals for large values).
     * For values >= 1000 or <= -1000: "1,2k €"
     */
    fun compactCurrency(amount: Double): String = formatMoneyShort(amount)

    /**
     * Signed formatting:
     *  -12.3 -> "- 12,30 €" (or "− 12,30 €")
     *   12.3 -> "+ 12,30 €"
     *    0.0 -> "0,00 €"
     */
    fun currencySigned(
        amount: Double,
        spaceAfterSign: Boolean = true,
        useUnicodeMinus: Boolean = false
    ): String {
        if (abs(amount) < EPS) return currency(0.0)

        val space = if (spaceAfterSign) " " else ""
        val minus = if (useUnicodeMinus) "−" else "-"
        val sign = if (amount > 0) "+" else minus

        return "$sign$space${currencyAbs(amount)}"
    }
}
