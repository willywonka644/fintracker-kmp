package io.github.willywonka644.fintracker.analytics

import io.github.willywonka644.fintracker.Booking
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

data class SawthoothBalanceResult(
    val points: List<BalanceDataPoint>,
    val billingBoundaryMillis: List<Long>,
)

/**
 * Computes a "sawtooth" balance series for a credit card account over a multi-period range.
 *
 * Within each billing cycle the running balance starts at 0 (or at the actual mid-cycle
 * balance for the first partial cycle when [fromDate] falls inside an existing cycle).
 * At every billing-cycle boundary the balance resets to 0 for the next cycle, producing
 * the characteristic sawtooth pattern across multiple billing periods.
 *
 * @param bookings         All posted bookings for the CC account (no date pre-filter).
 * @param billingStartDay  Day-of-month on which each billing cycle starts (1–28).
 * @param fromDate         First day of the display range (inclusive).
 * @param toDate           Last day of the display range (inclusive, clamped to today).
 */
fun calculateSawthoothBalance(
    bookings: List<Booking>,
    billingStartDay: Int,
    fromDate: LocalDate,
    toDate: LocalDate,
    tz: TimeZone = TimeZone.currentSystemDefault(),
): SawthoothBalanceResult {
    val today = Clock.System.todayIn(tz)
    val clampedTo = if (toDate > today) today else toDate
    if (fromDate > clampedTo) return SawthoothBalanceResult(emptyList(), emptyList())

    val firstCycleStart: LocalDate = if (fromDate.dayOfMonth >= billingStartDay) {
        LocalDate(fromDate.year, fromDate.monthNumber, billingStartDay)
    } else {
        LocalDate(fromDate.year, fromDate.monthNumber, billingStartDay).minus(1, DateTimeUnit.MONTH)
    }

    val allPoints = mutableListOf<BalanceDataPoint>()
    val boundaries = mutableListOf<Long>()
    var isFirst = true
    var cycleStart = firstCycleStart

    while (cycleStart <= clampedTo) {
        val cycleEnd = cycleStart.plus(1, DateTimeUnit.MONTH)
        val segmentFrom = if (isFirst && cycleStart < fromDate) fromDate else cycleStart
        val rawSegmentTo = cycleEnd.minus(1, DateTimeUnit.DAY)
        val segmentTo = if (rawSegmentTo > clampedTo) clampedTo else rawSegmentTo

        if (segmentFrom <= segmentTo) {
            val segmentFromMs = segmentFrom.atStartOfDayIn(tz).toEpochMilliseconds()
            val segmentToMs   = segmentTo.atStartOfDayIn(tz).toEpochMilliseconds() + 86_400_000L

            val startingBalance: Double = if (isFirst && cycleStart < fromDate) {
                val cycleStartMs = cycleStart.atStartOfDayIn(tz).toEpochMilliseconds()
                bookings.sumOf { b ->
                    val t = b.effectiveDate ?: b.timestamp
                    if (t >= cycleStartMs && t < segmentFromMs) b.amount else 0.0
                }
            } else 0.0

            val segmentBookings = bookings.filter { b ->
                val t = b.effectiveDate ?: b.timestamp
                t >= segmentFromMs && t < segmentToMs
            }

            allPoints += calculateBalanceOverTime(
                bookingsInPeriod = segmentBookings,
                startingBalance  = startingBalance,
                periodFromMillis = segmentFromMs,
                periodToMillis   = segmentToMs,
                timeZone         = tz,
            )

            val cycleEndMs = cycleEnd.atStartOfDayIn(tz).toEpochMilliseconds()
            if (cycleEnd <= clampedTo) {
                // Hard-reset to exactly 0 at each cycle boundary so the sawtooth
                // never jumps positive due to settlement payments at end of cycle.
                allPoints.add(BalanceDataPoint(dateMillis = cycleEndMs, balance = 0.0))
                boundaries += cycleEndMs
            }
        }

        isFirst = false
        cycleStart = cycleEnd
    }

    return SawthoothBalanceResult(allPoints, boundaries)
}
