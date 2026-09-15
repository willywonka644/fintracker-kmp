package io.github.willywonka644.fintracker.analytics

import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.AccountType
import io.github.willywonka644.fintracker.Booking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

class FinanceAnalyticsTest {

    private fun booking(
        id: String,
        accountId: String,
        amount: Double,
        timestamp: Long,
        category: String? = null,
        description: String = "test"
    ): Booking {
        return Booking(
            id = id,
            accountId = accountId,
            amount = amount,
            description = description,
            timestamp = timestamp,
            category = category
        )
    }

    @Test
    fun sumIncome_onlyPositiveAmounts() {
        val bookings = listOf(
            booking("1", "A", 100.0, 1000L),
            booking("2", "A", -50.0, 1000L),
            booking("3", "A", 20.0, 1000L)
        )

        assertApprox(120.0, sumIncome(bookings))
    }

    @Test
    fun sumExpenses_onlyNegativeAmounts_returnsNegativeSum() {
        val bookings = listOf(
            booking("1", "A", 100.0, 1000L),
            booking("2", "A", -50.0, 1000L),
            booking("3", "A", -20.0, 1000L)
        )

        assertApprox(-70.0, sumExpenses(bookings))
    }

    @Test
    fun netTotal_sumsAllAmounts() {
        val bookings = listOf(
            booking("1", "A", 100.0, 1000L),
            booking("2", "A", -30.0, 1000L),
            booking("3", "A", -20.0, 1000L)
        )

        assertApprox(50.0, netTotal(bookings))
    }

    @Test
    fun filterBookingsForAccount_filtersByAccountId() {
        val all = listOf(
            booking("1", "A", 10.0, 1000L),
            booking("2", "B", 20.0, 1000L),
            booking("3", "A", -5.0, 1000L)
        )

        val result = filterBookingsForAccount(
            allBookings = all,
            accountId = "A",
            period = allTimePeriod(),
            category = null
        )

        assertEquals(2, result.size)
        assertEquals(setOf("1", "3"), result.map { it.id }.toSet())
    }

    @Test
    fun filterBookingsForAccount_filtersByCategory_whenProvided() {
        val all = listOf(
            booking("1", "A", -10.0, 1000L, category = "Groceries"),
            booking("2", "A", -20.0, 1000L, category = "Rent"),
            booking("3", "A", -5.0, 1000L, category = null),
            booking("4", "A", -7.0, 1000L, category = "Groceries")
        )

        val groceriesOnly = filterBookingsForAccount(
            allBookings = all,
            accountId = "A",
            period = allTimePeriod(),
            category = "Groceries"
        )

        assertEquals(setOf("1", "4"), groceriesOnly.map { it.id }.toSet())
    }

    @Test
    fun filterBookingsForAccount_filtersByCustomPeriod_inclusiveExclusiveBoundaries() {
        // period: [1000, 2000)
        val period = PeriodFilter(
            type = PeriodType.CUSTOM,
            fromInclusive = 1000L,
            toExclusive = 2000L
        )

        val all = listOf(
            booking("before", "A", -1.0, 999L),
            booking("start", "A", -1.0, 1000L),
            booking("inside", "A", -1.0, 1999L),
            booking("end", "A", -1.0, 2000L)
        )

        val result = filterBookingsForAccount(
            allBookings = all,
            accountId = "A",
            period = period,
            category = null
        )

        assertEquals(setOf("start", "inside"), result.map { it.id }.toSet())
    }

    @Test
    fun topExpenseCategoryWithAmount_returnsCategoryWithMostNegativeSum_ignoresBlankAndNull() {
        val bookings = listOf(
            booking("1", "A", -200.0, 1000L, category = "Groceries"),
            booking("2", "A", -500.0, 1000L, category = "Rent"),
            booking("3", "A", -50.0, 1000L, category = "Groceries"),
            booking("4", "A", -999.0, 1000L, category = "   "), // ignored
            booking("5", "A", -10.0, 1000L, category = null),    // ignored
            booking("6", "A", +100.0, 1000L, category = "Rent")  // positive, ignored by expense grouping
        )

        val result = topExpenseCategoryWithAmount(bookings)

        // Groceries sum = -250, Rent sum = -500 => Rent is "top spending"
        assertEquals("Rent", result?.first)
        assertApprox(-500.0, result?.second ?: 0.0)
    }

    @Test
    fun topExpenseCategory_returnsNull_whenNoExpenseCategories() {
        val bookings = listOf(
            booking("1", "A", +10.0, 1000L, category = "Income"),
            booking("2", "A", -5.0, 1000L, category = null),
            booking("3", "A", -7.0, 1000L, category = "  ")
        )

        assertNull(topExpenseCategory(bookings))
    }

    @Test
    fun currentMonthPeriod_usesProvidedNowAndZoneId() {
        val zone = TimeZone.of("UTC")

        // 2026-01-15T12:00:00Z
        val nowMillis = Instant.parse("2026-01-15T12:00:00Z").toEpochMilliseconds()

        val period = currentMonthPeriod(nowMillis = nowMillis, timeZone = zone)

        val expectedFrom = Instant.parse("2026-01-01T00:00:00Z").toEpochMilliseconds()
        val expectedTo = Instant.parse("2026-02-01T00:00:00Z").toEpochMilliseconds()

        assertEquals(PeriodType.THIS_MONTH, period.type)
        assertEquals(expectedFrom, period.fromInclusive)
        assertEquals(expectedTo, period.toExclusive)
    }

    @Test
    fun currentBillingCyclePeriod_midCycle_includesStart_excludesEnd() {
        val zone = TimeZone.of("UTC")
        // 2026-01-25T10:00:00Z -> cycle should be [2026-01-20T00:00Z, 2026-02-20T00:00Z)
        val nowMillis = Instant.parse("2026-01-25T10:00:00Z").toEpochMilliseconds()

        val period = currentBillingCyclePeriod(
            billingStartDay = 20,
            nowMillis = nowMillis,
            timeZone = zone
        )

        val expectedFrom = Instant.parse("2026-01-20T00:00:00Z").toEpochMilliseconds()
        val expectedTo = Instant.parse("2026-02-20T00:00:00Z").toEpochMilliseconds()

        assertEquals(PeriodType.CUSTOM, period.type)
        assertEquals(expectedFrom, period.fromInclusive)
        assertEquals(expectedTo, period.toExclusive)
    }

    @Test
    fun currentBillingCyclePeriod_beforeStartDay_usesPreviousMonth() {
        val zone = TimeZone.of("UTC")
        // 2026-01-10T10:00:00Z -> cycle should be [2025-12-20T00:00Z, 2026-01-20T00:00Z)
        val nowMillis = Instant.parse("2026-01-10T10:00:00Z").toEpochMilliseconds()

        val period = currentBillingCyclePeriod(
            billingStartDay = 20,
            nowMillis = nowMillis,
            timeZone = zone
        )

        val expectedFrom = Instant.parse("2025-12-20T00:00:00Z").toEpochMilliseconds()
        val expectedTo = Instant.parse("2026-01-20T00:00:00Z").toEpochMilliseconds()

        assertEquals(expectedFrom, period.fromInclusive)
        assertEquals(expectedTo, period.toExclusive)
    }

    @Test
    fun calculateBillingCycleUsage_inclusiveExclusiveBoundaries() {
        val zone = TimeZone.of("UTC")
        // Window: [2026-01-20T00:00Z, 2026-02-20T00:00Z)
        val nowMillis = Instant.parse("2026-01-25T10:00:00Z").toEpochMilliseconds()
        val start = Instant.parse("2026-01-20T00:00:00Z").toEpochMilliseconds()
        val inside = Instant.parse("2026-01-21T12:00:00Z").toEpochMilliseconds()
        val end = Instant.parse("2026-02-20T00:00:00Z").toEpochMilliseconds()

        val account = Account(
            id = "A",
            name = "Card",
            type = AccountType.CREDIT_CARD,
            billingStartDay = 20,
            spendingLimit = 100.0
        )

        val all = listOf(
            booking("start", "A", -10.0, start),
            booking("inside", "A", -20.0, inside),
            booking("end", "A", -30.0, end),
            booking("positive", "A", 15.0, inside)
        )

        val usage = calculateBillingCycleUsage(
            account = account,
            allBookings = all,
            nowMillis = nowMillis,
            timeZone = zone
        )

        // start + inside included; end excluded; positive ignored
        assertApprox(30.0, usage?.spent ?: 0.0)
    }
}

private fun assertApprox(expected: Double, actual: Double, delta: Double = 0.0001) =
    assertTrue(kotlin.math.abs(expected - actual) <= delta, "Expected $expected, actual $actual")
