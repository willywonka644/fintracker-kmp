package io.github.willywonka644.fintracker.analytics

import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.BookingSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Money moving between the owner's own accounts is neither income nor expense (#118).
 *
 * [BookingSource.TRANSFER] is now the only test. The second half — a check on the category
 * name "Kreditkartenabrechnung" — stood here while the existing stock was still unmarked;
 * #122 and #123 marked it, and the check came out on 11.09.2026 after measuring that no
 * unmarked settlement was left and that the totals did not move.
 */
class ExcludeTransfersTest {

    private fun booking(
        id: String,
        amount: Double,
        source: BookingSource = BookingSource.MANUAL,
        category: String? = null,
        transferGroupId: String? = null,
    ) = Booking(
        id = id,
        accountId = "acc-1",
        amount = amount,
        description = "test",
        timestamp = 1_600_000_000_000,
        category = category,
        source = source,
        transferGroupId = transferGroupId,
    )

    @Test
    fun aTransferIsDroppedRegardlessOfItsCategory() {
        val bookings = listOf(
            booking("keep", -50.0, category = "Lebensmittel"),
            booking("out", -400.0, source = BookingSource.TRANSFER, transferGroupId = "g1"),
            booking("in", 400.0, source = BookingSource.TRANSFER, transferGroupId = "g1"),
        )

        val kept = bookings.excludeTransfers()

        assertEquals(listOf("keep"), kept.map { it.id })
    }

    @Test
    fun bothHalvesGoOrNeitherDoes() {
        // The point of the type: leaving one side in would move the totals by the
        // full amount in one direction, which is worse than counting both.
        val bookings = listOf(
            booking("out", -400.0, source = BookingSource.TRANSFER, transferGroupId = "g1"),
            booking("in", 400.0, source = BookingSource.TRANSFER, transferGroupId = "g1"),
        )

        assertTrue(bookings.excludeTransfers().isEmpty())
        assertEquals(0.0, sumIncome(bookings))
        assertEquals(0.0, sumExpenses(bookings))
    }

    @Test
    fun anUnmarkedSettlementIsAnOrdinaryExpenseAgain() {
        // The counterpart of the deleted rule: the name alone no longer excludes anything.
        // Measured before removing it (11.09.2026) — no such booking was left in the
        // database, and income and expenses moved by 0.00 €.
        val bookings = listOf(
            booking("settlement", -1251.38, category = "Kreditkartenabrechnung"),
            booking("groceries", -50.0, category = "Lebensmittel"),
        )

        assertEquals(listOf("settlement", "groceries"), bookings.excludeTransfers().map { it.id })
    }

    @Test
    fun aTransferWithoutACategoryIsStillDropped() {
        // The case the old rule missed in the real database: four settlement
        // counter-entries were filed under no category at all, so matching on the
        // name never saw them. The type does not depend on what they are called.
        val bookings = listOf(
            booking("counter-entry", 1251.38, source = BookingSource.TRANSFER, category = null),
        )

        assertTrue(bookings.excludeTransfers().isEmpty())
    }

    @Test
    fun anOrdinaryBookingIsUntouched() {
        val bookings = listOf(
            booking("salary", 2000.0),
            booking("rent", -800.0),
        )

        assertEquals(2, bookings.excludeTransfers().size)
        assertEquals(2000.0, sumIncome(bookings))
        assertEquals(-800.0, sumExpenses(bookings))
    }

    @Test
    fun categoryBreakdownCanBeAskedToKeepTransfers() {
        // The single-account view of a card still wants to see the settlement — there
        // it is the one entry that explains the balance.
        val bookings = listOf(
            booking("settlement", -400.0, source = BookingSource.TRANSFER, category = "Umbuchung"),
            booking("groceries", -100.0, category = "Lebensmittel"),
        )

        val filtered = calculateCategoryBreakdown(bookings, filterTransfers = true)
        val unfiltered = calculateCategoryBreakdown(bookings, filterTransfers = false)

        assertEquals(listOf("Lebensmittel"), filtered.map { it.categoryName })
        assertEquals(setOf("Lebensmittel", "Umbuchung"), unfiltered.map { it.categoryName }.toSet())
    }
}
