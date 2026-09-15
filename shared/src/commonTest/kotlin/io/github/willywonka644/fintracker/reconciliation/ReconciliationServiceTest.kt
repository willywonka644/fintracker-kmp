package io.github.willywonka644.fintracker.reconciliation

import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.BookingSource
import io.github.willywonka644.fintracker.BookingStatus
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tier-1b tests (Phase 6.5) — account reconciliation math.
 *
 * All timestamps are built in UTC so the assertions are independent of the
 * machine's system time zone.
 */
class ReconciliationServiceTest {

    private val utc = TimeZone.UTC
    private val account = "acc-1"

    private fun millisAt(date: LocalDate, hour: Int = 12): Long =
        LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, hour, 0)
            .toInstant(utc).toEpochMilliseconds()

    private fun booking(
        id: String,
        amount: Double,
        date: LocalDate,
        accountId: String = account,
        status: BookingStatus = BookingStatus.POSTED,
        source: BookingSource = BookingSource.MANUAL,
        effectiveDate: LocalDate? = null
    ) = Booking(
        id = id,
        accountId = accountId,
        amount = amount,
        description = "Buchung $id",
        timestamp = millisAt(date),
        status = status,
        source = source,
        effectiveDate = effectiveDate?.let { millisAt(it) }
    )

    private fun date(month: Int, day: Int) = LocalDate(2026, month, day)

    // ── computeExpectedBalance ─────────────────────────────────────────────────

    @Test
    fun computeExpectedBalance_sumsPostedBookingsUpToAndIncludingDate() {
        val bookings = listOf(
            booking("b1", -100.0, date(1, 10)),
            booking("b2", 50.0, date(1, 20))
        )
        // As of Jan 15: only b1 counts.
        assertEquals(-100.0, ReconciliationService.computeExpectedBalance(bookings, account, date(1, 15), utc))
        // As of Jan 20: both count.
        assertEquals(-50.0, ReconciliationService.computeExpectedBalance(bookings, account, date(1, 20), utc))
    }

    @Test
    fun computeExpectedBalance_excludesScheduledBookings() {
        val bookings = listOf(
            booking("posted", -100.0, date(1, 10)),
            booking("scheduled", -999.0, date(1, 12), status = BookingStatus.SCHEDULED)
        )
        assertEquals(-100.0, ReconciliationService.computeExpectedBalance(bookings, account, date(1, 15), utc))
    }

    @Test
    fun computeExpectedBalance_ignoresOtherAccounts() {
        val bookings = listOf(
            booking("mine", -100.0, date(1, 10)),
            booking("other", -500.0, date(1, 10), accountId = "acc-2")
        )
        assertEquals(-100.0, ReconciliationService.computeExpectedBalance(bookings, account, date(1, 15), utc))
    }

    @Test
    fun computeExpectedBalance_usesEffectiveDateOverTimestamp() {
        // Booked (timestamp) on Jan 25, but the money effectively moved on Jan 5.
        val bookings = listOf(
            booking("b1", -100.0, date(1, 25), effectiveDate = date(1, 5))
        )
        // As of Jan 15 the effectiveDate (Jan 5) makes it count.
        assertEquals(-100.0, ReconciliationService.computeExpectedBalance(bookings, account, date(1, 15), utc))
    }

    // ── createCorrectionBooking ────────────────────────────────────────────────

    @Test
    fun createCorrectionBooking_returnsNullWhenBalancesMatch() {
        val result = ReconciliationService.createCorrectionBooking(
            accountId = account, date = date(1, 15),
            expectedBalance = -50.0, actualBalance = -50.0,
            idProvider = { "should-not-be-used" }, timeZone = utc
        )
        assertNull(result)
    }

    @Test
    fun createCorrectionBooking_returnsNullForSubCentDifference() {
        val result = ReconciliationService.createCorrectionBooking(
            accountId = account, date = date(1, 15),
            expectedBalance = -50.0, actualBalance = -50.004,
            idProvider = { "x" }, timeZone = utc
        )
        assertNull(result, "Differences below 0.005 must not create a correction")
    }

    @Test
    fun createCorrectionBooking_bridgesGapWithReconciliationSourcedPostedBooking() {
        // Actual is higher than expected by 20 → correction of +20.
        val result = ReconciliationService.createCorrectionBooking(
            accountId = account, date = date(1, 15),
            expectedBalance = -50.0, actualBalance = -30.0,
            idProvider = { "corr-1" }, timeZone = utc
        )
        assertNotNull(result)
        assertEquals("corr-1", result.id)
        assertEquals(account, result.accountId)
        assertEquals(20.0, result.amount, absoluteTolerance = 1e-9)
        assertEquals(BookingSource.RECONCILIATION, result.source)
        assertEquals(BookingStatus.POSTED, result.status)
        // Timestamp anchored to noon of the reconciliation date.
        assertEquals(millisAt(date(1, 15)), result.timestamp)
    }

    // ── getReconciliationHistory ───────────────────────────────────────────────

    @Test
    fun getReconciliationHistory_reconstructsExpectedAndActualFromCorrection() {
        val bookings = listOf(
            booking("b1", -100.0, date(1, 10)),
            booking("corr", 20.0, date(1, 12), source = BookingSource.RECONCILIATION)
        )
        val history = ReconciliationService.getReconciliationHistory(bookings, account, utc)

        assertEquals(1, history.size)
        val entry = history.single()
        assertEquals("corr", entry.bookingId)
        assertEquals(date(1, 12), entry.date)
        assertEquals(20.0, entry.correctionAmount, absoluteTolerance = 1e-9)
        // Expected balance excludes the correction booking itself → just b1.
        assertEquals(-100.0, entry.expectedBalance, absoluteTolerance = 1e-9)
        assertEquals(-80.0, entry.actualBalance, absoluteTolerance = 1e-9)
    }

    @Test
    fun getReconciliationHistory_onlyIncludesReconciliationBookingsNewestFirst() {
        val bookings = listOf(
            booking("b1", -100.0, date(1, 10)),                                     // normal, ignored
            booking("corrA", 20.0, date(1, 12), source = BookingSource.RECONCILIATION),
            booking("corrB", -10.0, date(1, 22), source = BookingSource.RECONCILIATION)
        )
        val history = ReconciliationService.getReconciliationHistory(bookings, account, utc)

        assertEquals(2, history.size)
        assertTrue(history.none { it.bookingId == "b1" })
        // Sorted by timestamp descending → corrB (Jan 22) before corrA (Jan 12).
        assertEquals("corrB", history[0].bookingId)
        assertEquals("corrA", history[1].bookingId)
    }
}
