package io.github.willywonka644.fintracker.analytics

import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.BookingSource
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The three Auswertungen figures, and the rule that they add up (#100).
 *
 * The bug this replaces: the desktop's third tile summed every booking untouched while the
 * two beside it dropped corrections and settlements, so the tiles disagreed by 3,111.10 €
 * on the real database. The arithmetic is trivial; what matters is that one function now
 * answers all three, so the two screens cannot drift again.
 */
class PeriodTotalsTest {

    private fun booking(
        amount: Double,
        source: BookingSource = BookingSource.MANUAL,
    ) = Booking(
        id = "b${amount}${source}",
        accountId = "giro",
        amount = amount,
        description = "test",
        timestamp = 1_600_000_000_000,
        source = source,
    )

    @Test
    fun theResultIsAlwaysIncomeMinusExpense() {
        // The promise the label makes, and the one the desktop broke.
        val totals = periodTotals(
            listOf(booking(2000.0), booking(-800.0), booking(-50.0)),
            excludeTransfers = true,
        )

        assertEquals(2000.0, totals.income)
        assertEquals(850.0, totals.expense)
        assertEquals(1150.0, totals.result)
    }

    @Test
    fun bothSidesAreReportedPositive() {
        // The screens add their own sign; a negative expense here would print twice.
        val totals = periodTotals(listOf(booking(-800.0)), excludeTransfers = true)

        assertEquals(0.0, totals.income)
        assertEquals(800.0, totals.expense)
        assertEquals(-800.0, totals.result)
    }

    @Test
    fun correctionsNeverCount() {
        // A Kontoabgleich moves a balance to where it should have been; nothing flowed.
        val withCorrection = periodTotals(
            listOf(booking(2000.0), booking(-1836.15, source = BookingSource.RECONCILIATION)),
            excludeTransfers = true,
        )
        val without = periodTotals(listOf(booking(2000.0)), excludeTransfers = true)

        assertEquals(without, withCorrection)
    }

    @Test
    fun correctionsAreDroppedEvenWhenTransfersAreKept() {
        // The single-account view keeps settlements but must still drop corrections —
        // the desktop's old saldo kept both, which is where the 3,111.10 € came from.
        val totals = periodTotals(
            listOf(booking(2000.0), booking(1070.92, source = BookingSource.RECONCILIATION)),
            excludeTransfers = false,
        )

        assertEquals(2000.0, totals.income)
    }

    @Test
    fun transfersDropOnlyWhenTheCallerSaysSo() {
        val bookings = listOf(
            booking(2000.0),
            booking(-400.0, source = BookingSource.TRANSFER),
        )

        assertEquals(0.0, periodTotals(bookings, excludeTransfers = true).expense)
        assertEquals(400.0, periodTotals(bookings, excludeTransfers = false).expense)
    }

    @Test
    fun aTransferPairLeavesTheResultAlone() {
        // Both halves go together, so moving money between one's own accounts cannot
        // change whether the period was in the black.
        val plain = listOf(booking(2000.0), booking(-800.0))
        val withTransfer = plain + listOf(
            booking(-450.0, source = BookingSource.TRANSFER),
            booking(450.0, source = BookingSource.TRANSFER),
        )

        assertEquals(
            periodTotals(plain, excludeTransfers = true).result,
            periodTotals(withTransfer, excludeTransfers = true).result,
        )
    }

    @Test
    fun anEmptyPeriodIsZeroRatherThanUndefined() {
        val totals = periodTotals(emptyList(), excludeTransfers = true)

        assertEquals(0.0, totals.income)
        assertEquals(0.0, totals.expense)
        assertEquals(0.0, totals.result)
    }
}
