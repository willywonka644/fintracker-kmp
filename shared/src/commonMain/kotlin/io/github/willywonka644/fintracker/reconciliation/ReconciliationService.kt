package io.github.willywonka644.fintracker.reconciliation

import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.BookingSource
import io.github.willywonka644.fintracker.BookingStatus
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Represents a reconciliation event shown in the history list.
 * Derived from correction bookings (source == RECONCILIATION).
 */
data class ReconciliationEntry(
    val bookingId: String,
    val date: LocalDate,
    val expectedBalance: Double,
    val actualBalance: Double,
    val correctionAmount: Double
)

object ReconciliationService {

    /**
     * Computes the expected balance for the given account at [asOfDate],
     * i.e. the sum of all POSTED booking amounts whose timestamp falls
     * on or before the end of [asOfDate].
     */
    fun computeExpectedBalance(
        allBookings: List<Booking>,
        accountId: String,
        asOfDate: LocalDate,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): Double {
        val cutoffMillis = asOfDate.plus(1, DateTimeUnit.DAY)
            .atStartOfDayIn(timeZone)
            .toEpochMilliseconds()

        return allBookings
            .filter { it.accountId == accountId }
            .filter { it.status == BookingStatus.POSTED }
            .filter { (it.effectiveDate ?: it.timestamp) < cutoffMillis }
            .sumOf { it.amount }
    }

    /**
     * Creates a correction booking that bridges the gap between
     * the expected and actual balance at the given date.
     *
     * Returns null if no correction is needed (difference is zero).
     */
    fun createCorrectionBooking(
        accountId: String,
        date: LocalDate,
        expectedBalance: Double,
        actualBalance: Double,
        idProvider: () -> String,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): Booking? {
        val difference = actualBalance - expectedBalance
        if (kotlin.math.abs(difference) < 0.005) return null

        val timestamp = LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, 12, 0, 0, 0)
            .toInstant(timeZone)
            .toEpochMilliseconds()

        return Booking(
            id = idProvider(),
            accountId = accountId,
            amount = difference,
            description = "Kontoabgleich-Korrektur",
            timestamp = timestamp,
            source = BookingSource.RECONCILIATION,
            status = BookingStatus.POSTED,
            lastModifiedAt = Clock.System.now().toEpochMilliseconds()
        )
    }

    /**
     * Builds the reconciliation history from all correction bookings
     * for the given account. Each RECONCILIATION-source booking represents
     * one past reconciliation event.
     *
     * We reconstruct the expected/actual balances by subtracting the
     * correction from what the balance would have been before the correction.
     */
    fun getReconciliationHistory(
        allBookings: List<Booking>,
        accountId: String,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): List<ReconciliationEntry> {
        return allBookings
            .filter { it.accountId == accountId }
            .filter { it.source == BookingSource.RECONCILIATION }
            .sortedByDescending { it.timestamp }
            .map { booking ->
                val date = Instant.fromEpochMilliseconds(booking.timestamp)
                    .toLocalDateTime(timeZone)
                    .date
                val correctionAmount = booking.amount
                val cutoffMillis = date.plus(1, DateTimeUnit.DAY)
                    .atStartOfDayIn(timeZone)
                    .toEpochMilliseconds()
                val expectedBalance = allBookings
                    .filter { it.accountId == accountId }
                    .filter { it.status == BookingStatus.POSTED }
                    .filter { it.id != booking.id }
                    .filter { (it.effectiveDate ?: it.timestamp) < cutoffMillis }
                    .sumOf { it.amount }

                ReconciliationEntry(
                    bookingId = booking.id,
                    date = date,
                    expectedBalance = expectedBalance,
                    actualBalance = expectedBalance + correctionAmount,
                    correctionAmount = correctionAmount
                )
            }
    }
}
