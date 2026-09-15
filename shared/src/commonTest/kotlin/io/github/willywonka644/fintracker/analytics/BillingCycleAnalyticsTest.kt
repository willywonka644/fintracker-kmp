package io.github.willywonka644.fintracker.analytics

import io.github.willywonka644.fintracker.Booking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tier-1c tests (Phase 6.5) — billing-cycle analytics.
 *
 * Covers the gaps left by FinanceAnalyticsTest: credit-card limit usage,
 * the billingStartDay 1..28 guard (regression for the old 29–31 crash on
 * desktop) and income/expense grouping per billing cycle.
 */
class BillingCycleAnalyticsTest {

    private val utc = TimeZone.UTC

    private fun millisAt(date: LocalDate, hour: Int = 12): Long =
        LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, hour, 0)
            .toInstant(utc).toEpochMilliseconds()

    private fun d(month: Int, day: Int) = LocalDate(2026, month, day)

    private fun booking(id: String, amount: Double, date: LocalDate) = Booking(
        id = id, accountId = "acc", amount = amount,
        description = "b$id", timestamp = millisAt(date)
    )

    // ── computeLimitUsage ──────────────────────────────────────────────────────

    @Test
    fun computeLimitUsage_negativeBalanceCountsAsUsed() {
        val usage = computeLimitUsage(currentBalance = -300.0, limit = 1000.0)
        assertEquals(300.0, usage.used, absoluteTolerance = 1e-9)
        assertEquals(700.0, usage.remaining, absoluteTolerance = 1e-9)
        assertEquals(0.3, usage.percentUsed, absoluteTolerance = 1e-9)
        assertFalse(usage.overLimit)
    }

    @Test
    fun computeLimitUsage_positiveBalanceMeansNothingUsed() {
        val usage = computeLimitUsage(currentBalance = 200.0, limit = 1000.0)
        assertEquals(0.0, usage.used, absoluteTolerance = 1e-9)
        assertEquals(1000.0, usage.remaining, absoluteTolerance = 1e-9)
        assertFalse(usage.overLimit)
    }

    @Test
    fun computeLimitUsage_overLimitIsFlagged() {
        val usage = computeLimitUsage(currentBalance = -1200.0, limit = 1000.0)
        assertEquals(1200.0, usage.used, absoluteTolerance = 1e-9)
        assertEquals(-200.0, usage.remaining, absoluteTolerance = 1e-9)
        assertTrue(usage.overLimit)
    }

    @Test
    fun computeLimitUsage_zeroLimitDoesNotDivideByZero() {
        val usage = computeLimitUsage(currentBalance = -50.0, limit = 0.0)
        assertEquals(0.0, usage.percentUsed, absoluteTolerance = 1e-9)
        assertTrue(usage.overLimit) // 50 used > 0 limit
    }

    // ── billingStartDay 1..28 guard (regression: old 29–31 crash) ──────────────

    @Test
    fun currentBillingCyclePeriod_rejectsDayAbove28() {
        assertFailsWith<IllegalArgumentException> {
            currentBillingCyclePeriod(billingStartDay = 29, nowMillis = millisAt(d(3, 20)), timeZone = utc)
        }
    }

    @Test
    fun currentBillingCyclePeriod_rejectsDayBelow1() {
        assertFailsWith<IllegalArgumentException> {
            currentBillingCyclePeriod(billingStartDay = 0, nowMillis = millisAt(d(3, 20)), timeZone = utc)
        }
    }

    @Test
    fun currentBillingCyclePeriod_acceptsBoundaryDays1And28() {
        // Should not throw.
        currentBillingCyclePeriod(billingStartDay = 1, nowMillis = millisAt(d(3, 20)), timeZone = utc)
        currentBillingCyclePeriod(billingStartDay = 28, nowMillis = millisAt(d(3, 20)), timeZone = utc)
    }

    @Test
    fun generateBillingCyclePeriods_rejectsInvalidDay() {
        assertFailsWith<IllegalArgumentException> {
            generateBillingCyclePeriods(billingStartDay = 31, nowMillis = millisAt(d(3, 20)), timeZone = utc)
        }
    }

    // ── generateBillingCyclePeriods ────────────────────────────────────────────

    @Test
    fun generateBillingCyclePeriods_listsCyclesFromJan2026UpToNow() {
        val periods = generateBillingCyclePeriods(
            billingStartDay = 18, nowMillis = millisAt(d(3, 20)), timeZone = utc
        )
        // Cycles starting 18.01, 18.02, 18.03 are all on/before 20.03.
        assertEquals(3, periods.size)
        assertEquals("18.01.–17.02.2026", periods.first().label)
    }

    // ── calculateBillingCycleIncomeExpense ─────────────────────────────────────

    @Test
    fun calculateBillingCycleIncomeExpense_groupsAndSplitsPerCycle() {
        val bookings = listOf(
            booking("in", 1000.0, d(2, 20)),   // cycle 18.02–17.03
            booking("ex", -200.0, d(2, 25)),   // same cycle
            booking("ex2", -50.0, d(3, 25))    // cycle 18.03–17.04
        )
        val result = calculateBillingCycleIncomeExpense(bookings, billingStartDay = 18, timeZone = utc)

        assertEquals(2, result.size)
        // Sorted chronologically → Feb cycle first.
        assertEquals(1000.0, result[0].income, absoluteTolerance = 1e-9)
        assertEquals(200.0, result[0].expense, absoluteTolerance = 1e-9)
        assertEquals(0.0, result[1].income, absoluteTolerance = 1e-9)
        assertEquals(50.0, result[1].expense, absoluteTolerance = 1e-9)
    }

    @Test
    fun calculateBillingCycleIncomeExpense_emptyInputYieldsEmptyList() {
        assertEquals(emptyList<MonthlyIncomeExpense>(), calculateBillingCycleIncomeExpense(emptyList(), 18, utc))
    }
}
