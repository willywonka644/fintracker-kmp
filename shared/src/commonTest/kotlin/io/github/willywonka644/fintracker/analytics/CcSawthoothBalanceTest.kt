package io.github.willywonka644.fintracker.analytics

import io.github.willywonka644.fintracker.Booking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tier-1c tests (Phase 6.5) — credit-card sawtooth balance series.
 *
 * The defining behaviour: within a billing cycle the running balance
 * accumulates, and at every cycle boundary it hard-resets to exactly 0 so a
 * settlement payment at the end of a cycle never makes the curve jump positive.
 *
 * The range used here (Jan–Mar 2026) is entirely in the past relative to the
 * app's real "today", so the internal today-clamping never interferes.
 */
class CcSawthoothBalanceTest {

    private val utc = TimeZone.UTC

    private fun startOfDayMs(date: LocalDate): Long =
        date.atStartOfDayIn(utc).toEpochMilliseconds()

    private fun millisAt(date: LocalDate, hour: Int = 12): Long =
        LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, hour, 0)
            .toInstant(utc).toEpochMilliseconds()

    private fun d(month: Int, day: Int) = LocalDate(2026, month, day)

    private fun booking(id: String, amount: Double, date: LocalDate) = Booking(
        id = id, accountId = "cc", amount = amount,
        description = "b$id", timestamp = millisAt(date)
    )

    @Test
    fun sawtooth_resetsToZeroAtEachCycleBoundary() {
        // billingStartDay 18. Range spans two full cycles:
        //   cycle 1: 18.01–17.02   (booking -100 on 20.01)
        //   cycle 2: 18.02–17.03   (booking  -50 on 20.02)
        val bookings = listOf(
            booking("c1", -100.0, d(1, 20)),
            booking("c2", -50.0, d(2, 20))
        )

        val result = calculateSawthoothBalance(
            bookings = bookings,
            billingStartDay = 18,
            fromDate = d(1, 18),
            toDate = d(3, 17),
            tz = utc
        )

        val feb18 = startOfDayMs(d(2, 18))

        // Exactly one interior boundary: the 18.02 reset. The final cycle's end
        // (18.03) lies beyond the clamped range, so it produces no reset point.
        assertEquals(listOf(feb18), result.billingBoundaryMillis)

        // The boundary carries an explicit hard-reset point at balance 0.
        assertTrue(
            result.points.any { it.dateMillis == feb18 && it.balance == 0.0 },
            "Expected a 0.0 reset point at the 18.02 cycle boundary"
        )

        // Cycle 1 reached -100 before the reset...
        assertTrue(result.points.any { it.balance == -100.0 }, "Cycle 1 should reach -100")
        // ...and cycle 2 ends at -50 (no trailing reset).
        assertEquals(-50.0, result.points.last().balance, absoluteTolerance = 1e-9)
    }

    @Test
    fun sawtooth_emptyWhenRangeInverted() {
        val result = calculateSawthoothBalance(
            bookings = listOf(booking("x", -10.0, d(1, 20))),
            billingStartDay = 18,
            fromDate = d(3, 17),
            toDate = d(1, 18), // to < from
            tz = utc
        )
        assertTrue(result.points.isEmpty())
        assertTrue(result.billingBoundaryMillis.isEmpty())
    }
}
