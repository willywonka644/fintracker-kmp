package io.github.willywonka644.fintracker.installments

import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.BookingStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tier-1d tests (Phase 6.5) — installment grouping.
 *
 * nextDate is derived using the system time zone inside the production code, so
 * assertions only check whether a next date exists (non-null), never its exact
 * value — keeping the test independent of the machine's time zone.
 */
class InstallmentGroupingTest {

    private val account = "acc"

    private fun installment(
        id: String,
        groupId: String,
        amount: Double,
        timestamp: Long,
        status: BookingStatus,
        description: String = "Laptop",
        category: String? = "Elektronik",
        accountId: String = account
    ) = Booking(
        id = id, accountId = accountId, amount = amount, description = description,
        timestamp = timestamp, category = category, status = status,
        installmentGroupId = groupId
    )

    @Test
    fun buildInstallmentGroups_aggregatesCountsAmountsAndMetadata() {
        val bookings = listOf(
            installment("1", "g1", -100.0, 1_000L, BookingStatus.POSTED),
            installment("2", "g1", -100.0, 2_000L, BookingStatus.POSTED),
            installment("3", "g1", -100.0, 3_000L, BookingStatus.SCHEDULED),
            // Non-installment booking on same account must be ignored.
            Booking(id = "x", accountId = account, amount = -5.0, description = "Kaffee", timestamp = 500L)
        )

        val groups = buildInstallmentGroups(bookings, account)
        assertEquals(1, groups.size)

        val g = groups.single()
        assertEquals("g1", g.groupId)
        assertEquals(-300.0, g.totalAmount, absoluteTolerance = 1e-9)
        assertEquals(-100.0, g.installmentAmount, absoluteTolerance = 1e-9)
        assertEquals(3, g.totalCount)
        assertEquals(2, g.paidCount)
        assertEquals(1, g.remainingCount)
        assertEquals("Laptop", g.description)
        assertEquals("Elektronik", g.category)
        assertNotNull(g.nextDate) // one scheduled installment remains
    }

    @Test
    fun buildInstallmentGroups_fullyPaidGroupHasNoNextDate() {
        val bookings = listOf(
            installment("1", "g1", -100.0, 1_000L, BookingStatus.POSTED),
            installment("2", "g1", -100.0, 2_000L, BookingStatus.POSTED)
        )

        val g = buildInstallmentGroups(bookings, account).single()
        assertEquals(0, g.remainingCount)
        assertNull(g.nextDate)
    }

    @Test
    fun buildInstallmentGroups_filtersByAccount() {
        val bookings = listOf(
            installment("1", "g1", -100.0, 1_000L, BookingStatus.POSTED, accountId = "acc"),
            installment("2", "g2", -50.0, 1_000L, BookingStatus.POSTED, accountId = "other")
        )

        val groups = buildInstallmentGroups(bookings, "acc")
        assertEquals(1, groups.size)
        assertEquals("g1", groups.single().groupId)
    }

    @Test
    fun buildInstallmentGroups_ordersUnfinishedGroupsFirst() {
        val bookings = listOf(
            // g-done: fully paid
            installment("1", "g-done", -100.0, 1_000L, BookingStatus.POSTED),
            // g-open: still has a scheduled installment
            installment("2", "g-open", -100.0, 1_000L, BookingStatus.POSTED),
            installment("3", "g-open", -100.0, 2_000L, BookingStatus.SCHEDULED)
        )

        val groups = buildInstallmentGroups(bookings, account)
        assertEquals(2, groups.size)
        // Groups with remaining installments sort first.
        assertTrue(groups.first().remainingCount > 0)
        assertEquals("g-open", groups.first().groupId)
    }
}
