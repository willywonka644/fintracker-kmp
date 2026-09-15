package io.github.willywonka644.fintracker.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tier-1d tests (Phase 6.5) — money formatting sign logic.
 *
 * The numeric formatting itself is a platform expect/actual (locale-dependent),
 * so these assertions only pin down the sign handling, which is common code.
 */
class MoneyFormatTest {

    @Test
    fun currencySigned_prefixesPlusForPositive() {
        assertTrue(MoneyFormat.currencySigned(12.3).startsWith("+"))
    }

    @Test
    fun currencySigned_prefixesMinusForNegative() {
        assertTrue(MoneyFormat.currencySigned(-12.3).startsWith("-"))
    }

    @Test
    fun currencySigned_usesUnicodeMinusWhenRequested() {
        assertTrue(MoneyFormat.currencySigned(-12.3, useUnicodeMinus = true).startsWith("−"))
    }

    @Test
    fun currencySigned_zeroHasNoSign() {
        // Exactly zero and sub-epsilon values both render as plain zero currency.
        assertEquals(MoneyFormat.currency(0.0), MoneyFormat.currencySigned(0.0))
        assertEquals(MoneyFormat.currency(0.0), MoneyFormat.currencySigned(0.00000005))
    }
}
