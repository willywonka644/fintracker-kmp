package io.github.willywonka644.fintracker.analytics

import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.AccountType
import io.github.willywonka644.fintracker.Booking
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.max

/**
 * Usage metrics for the current billing cycle.
 *
 * spent is absolute expenses (positive number).
 * remaining can be negative if overspent.
 * percentUsed can be > 1.0 if overspent (this is useful for warnings later).
 */
data class BillingCycleUsage(
    val period: PeriodFilter,
    val limit: Double,
    val spent: Double,
    val remaining: Double,
    val percentUsed: Double
)

/**
 * Usage metrics for credit card limit based on current balance.
 */
data class LimitUsage(
    val limit: Double,
    val used: Double,
    val remaining: Double,
    val percentUsed: Double,
    val overLimit: Boolean
)

/**
 * Builds a PeriodFilter (CUSTOM) representing the current billing cycle window:
 * [fromInclusive, toExclusive)
 *
 * billingStartDay must be 1..28 (safe across months).
 */
fun currentBillingCyclePeriod(
    billingStartDay: Int,
    nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): PeriodFilter {
    require(billingStartDay in 1..28) { "billingStartDay must be 1..28 (got $billingStartDay)" }

    val nowDate = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(timeZone).date

    val startDate: LocalDate
    val endDate: LocalDate

    if (nowDate.dayOfMonth >= billingStartDay) {
        startDate = LocalDate(nowDate.year, nowDate.monthNumber, billingStartDay)
        endDate = startDate.plus(1, DateTimeUnit.MONTH)
    } else {
        endDate = LocalDate(nowDate.year, nowDate.monthNumber, billingStartDay)
        startDate = endDate.minus(1, DateTimeUnit.MONTH)
    }

    return PeriodFilter(
        type = PeriodType.CUSTOM,
        fromInclusive = startDate.atStartOfDayIn(timeZone).toEpochMilliseconds(),
        toExclusive = endDate.atStartOfDayIn(timeZone).toEpochMilliseconds()
    )
}

/**
 * Generates a list of billing-cycle-based selectable periods from
 * the first cycle starting on/after 18.01.2026 up to the current cycle.
 *
 * Each cycle spans [billingStartDay of month N, billingStartDay of month N+1).
 * Label format: "18.01.–17.02.2026"
 */
fun generateBillingCyclePeriods(
    billingStartDay: Int = 18,
    nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): List<SelectablePeriod> {
    require(billingStartDay in 1..28) { "billingStartDay must be 1..28 (got $billingStartDay)" }

    val nowDate = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(timeZone).date

    var cycleStart = LocalDate(2026, 1, billingStartDay)

    val periods = mutableListOf<SelectablePeriod>()
    while (cycleStart <= nowDate) {
        val cycleEnd = cycleStart.plus(1, DateTimeUnit.MONTH)
        val lastDay = cycleEnd.minus(1, DateTimeUnit.DAY)
        val label = "%d.%02d.–%d.%02d.%d".format(
            cycleStart.dayOfMonth,
            cycleStart.monthNumber,
            lastDay.dayOfMonth,
            lastDay.monthNumber,
            lastDay.year
        )
        periods += SelectablePeriod(
            label = label,
            filter = PeriodFilter(
                type = PeriodType.CUSTOM,
                fromInclusive = cycleStart.atStartOfDayIn(timeZone).toEpochMilliseconds(),
                toExclusive = cycleEnd.atStartOfDayIn(timeZone).toEpochMilliseconds()
            )
        )
        cycleStart = cycleEnd
    }
    return periods
}

/**
 * Calculates spending limit usage for the current billing cycle.
 *
 * Rules:
 * - If account.spendingLimit is null -> return null (no limit, no usage)
 * - If CREDIT_CARD and billingStartDay is null -> assume 18 (your current business rule)
 * - Spent = sum(abs(amount)) for amount < 0 within the cycle
 */
fun calculateBillingCycleUsage(
    account: Account,
    allBookings: List<Booking>,
    nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): BillingCycleUsage? {
    val limit = account.spendingLimit ?: return null

    val startDay: Int? = when (account.type) {
        AccountType.CREDIT_CARD -> account.billingStartDay ?: 18
        else -> account.billingStartDay
    }

    // If GIRO is missing a start day, we can't compute a cycle
    if (startDay == null) return null

    val period = currentBillingCyclePeriod(
        billingStartDay = startDay,
        nowMillis = nowMillis,
        timeZone = timeZone
    )

    val periodBookings = filterBookingsForAccount(
        allBookings = allBookings,
        accountId = account.id,
        period = period,
        category = null
    )

    val spent = periodBookings
        .asSequence()
        .filter { it.amount < 0.0 }
        .sumOf { abs(it.amount) }

    val remaining = limit - spent
    val percentUsed = if (limit == 0.0) 0.0 else (spent / limit)

    return BillingCycleUsage(
        period = period,
        limit = limit,
        spent = spent,
        remaining = remaining,
        percentUsed = percentUsed
    )
}

/**
 * Calculates income vs expense totals grouped by billing cycle periods.
 *
 * Each billing cycle spans [billingStartDay of month N, billingStartDay of month N+1).
 * Label format: "DD.MM.–DD.MM." (e.g. "18.02.–17.03.")
 *
 * Only cycles that contain at least one booking are included.
 * Result is sorted chronologically.
 *
 * Pure function — no Android imports.
 */
fun calculateBillingCycleIncomeExpense(
    bookings: List<Booking>,
    billingStartDay: Int,
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): List<MonthlyIncomeExpense> {
    if (bookings.isEmpty()) return emptyList()
    require(billingStartDay in 1..28)

    data class CycleKey(val startMillis: Long, val endMillis: Long, val label: String)

    fun cycleForTimestamp(ts: Long): CycleKey {
        val dateLocal = Instant.fromEpochMilliseconds(ts).toLocalDateTime(timeZone).date

        val cycleStartDate: LocalDate
        val cycleEndDate: LocalDate

        if (dateLocal.dayOfMonth >= billingStartDay) {
            cycleStartDate = LocalDate(dateLocal.year, dateLocal.monthNumber, billingStartDay)
            cycleEndDate = cycleStartDate.plus(1, DateTimeUnit.MONTH)
        } else {
            cycleEndDate = LocalDate(dateLocal.year, dateLocal.monthNumber, billingStartDay)
            cycleStartDate = cycleEndDate.minus(1, DateTimeUnit.MONTH)
        }

        val lastDay = cycleEndDate.minus(1, DateTimeUnit.DAY)
        val label = "%d.%02d.–%d.%02d.".format(
            cycleStartDate.dayOfMonth, cycleStartDate.monthNumber,
            lastDay.dayOfMonth, lastDay.monthNumber
        )
        return CycleKey(
            cycleStartDate.atStartOfDayIn(timeZone).toEpochMilliseconds(),
            cycleEndDate.atStartOfDayIn(timeZone).toEpochMilliseconds(),
            label
        )
    }

    return bookings
        .groupBy { booking ->
            val ts = booking.effectiveDate ?: booking.timestamp
            cycleForTimestamp(ts)
        }
        .toSortedMap(compareBy { it.startMillis })
        .map { (cycle, cycleBookings) ->
            val income = cycleBookings.filter { it.amount > 0.0 }.sumOf { it.amount }
            val expense = cycleBookings.filter { it.amount < 0.0 }.sumOf { abs(it.amount) }
            MonthlyIncomeExpense(
                monthLabel = cycle.label,
                income = income,
                expense = expense,
                fromMillis = cycle.startMillis,
                toMillisExclusive = cycle.endMillis,
            )
        }
}

fun computeLimitUsage(currentBalance: Double, limit: Double): LimitUsage {
    val used = max(0.0, -currentBalance)
    val remaining = limit - used
    val percentUsed = if (limit == 0.0) 0.0 else (used / limit)
    return LimitUsage(
        limit = limit,
        used = used,
        remaining = remaining,
        percentUsed = percentUsed,
        overLimit = used > limit
    )
}
