package io.github.willywonka644.fintracker.analytics

import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.BookingSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The category breakdown can be asked about income too (#127).
 *
 * It only ever answered "where did the money go", although income carries categories
 * as well and "which source brought how much" is the same shape of question. The side
 * is a parameter now; the default stays expenses, so every existing caller keeps its
 * answer.
 */
class CategoryBreakdownSideTest {

    private fun booking(
        id: String,
        amount: Double,
        category: String? = null,
        source: BookingSource = BookingSource.MANUAL,
    ) = Booking(
        id = id,
        accountId = "acc-1",
        amount = amount,
        description = "test",
        timestamp = 1_600_000_000_000,
        category = category,
        source = source,
    )

    private val mixed = listOf(
        booking("s1", 2000.0, category = "Gehalt"),
        booking("s2", 500.0, category = "Gehalt"),
        booking("s3", 500.0, category = "Nebenjob"),
        booking("e1", -800.0, category = "Miete"),
        booking("e2", -200.0, category = "Lebensmittel"),
    )

    @Test
    fun theIncomeSideGroupsWhatCameInAndLeavesTheSpendingOut() {
        val income = calculateCategoryBreakdown(mixed, side = CategoryBreakdownSide.INCOME)

        assertEquals(listOf("Gehalt", "Nebenjob"), income.map { it.categoryName })
        assertEquals(2500.0, income.first().amount)
        // 2500 of 3000.
        assertEquals(1.0, income.sumOf { it.percentage }, absoluteTolerance = 1e-9)
        assertTrue(income.all { it.amount > 0.0 }, "amounts are positive on both sides")
    }

    @Test
    fun theExpenseSideIsStillTheDefaultAndUnchanged() {
        val byDefault = calculateCategoryBreakdown(mixed)
        val explicit = calculateCategoryBreakdown(mixed, side = CategoryBreakdownSide.EXPENSES)

        assertEquals(explicit, byDefault)
        assertEquals(listOf("Miete", "Lebensmittel"), byDefault.map { it.categoryName })
        assertEquals(800.0, byDefault.first().amount)
    }

    @Test
    fun aTransferIsNoMoreAnIncomeThanItIsAnExpense() {
        val withTransfer = mixed + booking("t1", 400.0, category = "Umbuchung", source = BookingSource.TRANSFER)

        val filtered = calculateCategoryBreakdown(withTransfer, side = CategoryBreakdownSide.INCOME)
        val unfiltered = calculateCategoryBreakdown(
            withTransfer,
            filterTransfers = false,
            side = CategoryBreakdownSide.INCOME,
        )

        assertTrue(filtered.none { it.categoryName == "Umbuchung" }, "a transfer is not income")
        assertTrue(unfiltered.any { it.categoryName == "Umbuchung" }, "unfiltered still sees it")
    }

    @Test
    fun aPeriodWithoutIncomeYieldsNothingRatherThanZeroRows() {
        val onlySpending = listOf(booking("e1", -800.0, category = "Miete"))

        assertEquals(emptyList(), calculateCategoryBreakdown(onlySpending, side = CategoryBreakdownSide.INCOME))
    }
}
