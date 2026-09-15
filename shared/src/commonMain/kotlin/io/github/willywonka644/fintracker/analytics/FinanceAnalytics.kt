package io.github.willywonka644.fintracker.analytics

import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.BookingSource
import io.github.willywonka644.fintracker.BookingStatus
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime

/**
 * Sprint 3 / Step 1:
 * Pure functions for filtering + aggregations.
 *
 * Time range is [fromInclusive, toExclusive) to avoid boundary bugs.
 */

enum class PeriodType {
    ALL_TIME,
    THIS_MONTH,
    CUSTOM
}

data class PeriodFilter(
    val type: PeriodType,
    val fromInclusive: Long? = null,
    val toExclusive: Long? = null
) {
    init {
        if (type == PeriodType.CUSTOM) {
            require(
                (fromInclusive == null && toExclusive == null) ||
                        (fromInclusive != null && toExclusive != null)
            ) { "CUSTOM PeriodFilter must have both fromInclusive and toExclusive, or neither." }

            if (fromInclusive != null && toExclusive != null) {
                require(fromInclusive <= toExclusive) { "fromInclusive must be <= toExclusive." }
            }
        }
    }
}

fun allTimePeriod(): PeriodFilter = PeriodFilter(type = PeriodType.ALL_TIME)

fun currentMonthPeriod(
    nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): PeriodFilter {
    val nowLdt = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(timeZone)
    val firstOfMonth = LocalDate(nowLdt.year, nowLdt.monthNumber, 1)

    val from = firstOfMonth.atStartOfDayIn(timeZone).toEpochMilliseconds()
    val to = firstOfMonth.plus(1, DateTimeUnit.MONTH).atStartOfDayIn(timeZone).toEpochMilliseconds()

    return PeriodFilter(
        type = PeriodType.THIS_MONTH,
        fromInclusive = from,
        toExclusive = to
    )
}

/**
 * Represents a user-selectable time period with a display label and the
 * corresponding PeriodFilter for querying bookings.
 */
data class SelectablePeriod(
    val label: String,
    val filter: PeriodFilter
)

/**
 * Generates a list of month-based selectable periods from January 2026
 * up to and including the current month.
 *
 * Each period spans [1st of month 00:00, 1st of next month 00:00).
 * The last entry is the current month.
 */
fun generateMonthPeriods(
    nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): List<SelectablePeriod> {
    val nowLdt = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(timeZone)
    val currentFirstOfMonth = LocalDate(nowLdt.year, nowLdt.monthNumber, 1)
    val startFirstOfMonth = LocalDate(2026, 1, 1)

    val periods = mutableListOf<SelectablePeriod>()
    var firstOfMonth = startFirstOfMonth
    while (firstOfMonth <= currentFirstOfMonth) {
        val from = firstOfMonth.atStartOfDayIn(timeZone).toEpochMilliseconds()
        val to = firstOfMonth.plus(1, DateTimeUnit.MONTH).atStartOfDayIn(timeZone).toEpochMilliseconds()
        val label = "${monthName(firstOfMonth.monthNumber)} ${firstOfMonth.year}"
        periods += SelectablePeriod(
            label = label,
            filter = PeriodFilter(
                type = PeriodType.CUSTOM,
                fromInclusive = from,
                toExclusive = to
            )
        )
        firstOfMonth = firstOfMonth.plus(1, DateTimeUnit.MONTH)
    }
    return periods
}

/**
 * Returns the current month as a PeriodFilter (convenience for the overview screen).
 */
fun currentMonthFilter(
    nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): PeriodFilter {
    val nowLdt = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(timeZone)
    val firstOfMonth = LocalDate(nowLdt.year, nowLdt.monthNumber, 1)
    val from = firstOfMonth.atStartOfDayIn(timeZone).toEpochMilliseconds()
    val to = firstOfMonth.plus(1, DateTimeUnit.MONTH).atStartOfDayIn(timeZone).toEpochMilliseconds()
    return PeriodFilter(
        type = PeriodType.CUSTOM,
        fromInclusive = from,
        toExclusive = to
    )
}

fun lastMonthFilter(
    nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): PeriodFilter {
    val nowLdt = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(timeZone)
    val firstOfThisMonth = LocalDate(nowLdt.year, nowLdt.monthNumber, 1)
    val firstOfLastMonth = firstOfThisMonth.minus(1, DateTimeUnit.MONTH)
    val from = firstOfLastMonth.atStartOfDayIn(timeZone).toEpochMilliseconds()
    val to = firstOfThisMonth.atStartOfDayIn(timeZone).toEpochMilliseconds()
    return PeriodFilter(type = PeriodType.CUSTOM, fromInclusive = from, toExclusive = to)
}

fun last90DaysFilter(
    nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): PeriodFilter {
    val today = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(timeZone).date
    val to = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone).toEpochMilliseconds()
    val from = today.minus(89, DateTimeUnit.DAY).atStartOfDayIn(timeZone).toEpochMilliseconds()
    return PeriodFilter(type = PeriodType.CUSTOM, fromInclusive = from, toExclusive = to)
}

fun customRangeFilter(
    fromDate: LocalDate,
    toDate: LocalDate,
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): PeriodFilter {
    // Normalize swapped input: users can pick "Von" after "Bis" in the date pickers.
    // Without this, PeriodFilter's require(from <= to) would crash the app.
    val start = minOf(fromDate, toDate)
    val end = maxOf(fromDate, toDate)
    val from = start.atStartOfDayIn(timeZone).toEpochMilliseconds()
    val to = end.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone).toEpochMilliseconds()
    return PeriodFilter(type = PeriodType.CUSTOM, fromInclusive = from, toExclusive = to)
}

private fun monthName(month: Int): String = when (month) {
    1 -> "Januar"
    2 -> "Februar"
    3 -> "März"
    4 -> "April"
    5 -> "Mai"
    6 -> "Juni"
    7 -> "Juli"
    8 -> "August"
    9 -> "September"
    10 -> "Oktober"
    11 -> "November"
    12 -> "Dezember"
    else -> ""
}

fun filterBookingsForAccount(
    allBookings: List<Booking>,
    accountId: String,
    period: PeriodFilter,
    category: String? = null,
    includeScheduled: Boolean = false
): List<Booking> {
    val normalizedCategory = category?.trim()?.takeIf { it.isNotEmpty() }

    val (fromInc, toExc) = when (period.type) {
        PeriodType.ALL_TIME -> null to null
        PeriodType.THIS_MONTH, PeriodType.CUSTOM -> period.fromInclusive to period.toExclusive
    }

    return allBookings.asSequence()
        .filter { it.accountId == accountId }
        .filter { includeScheduled || it.status != BookingStatus.SCHEDULED }
        .filter { booking ->
            // Use effectiveDate when present, fall back to timestamp
            val t = booking.effectiveDate ?: booking.timestamp
            val afterStart = fromInc?.let { t >= it } ?: true
            val beforeEnd = toExc?.let { t < it } ?: true
            afterStart && beforeEnd
        }
        .filter { booking ->
            if (normalizedCategory == null) true
            else (booking.category?.trim() == normalizedCategory)
        }
        .toList()
}

/**
 * Drops what is not real income or expense: money moving between the owner's own accounts.
 *
 * [BookingSource.TRANSFER] is the whole test. It used to be joined by a check on the
 * category name "Kreditkartenabrechnung", which was how settlements were recognised before
 * the type existed — a string the user could rename, and one that had already missed the
 * counter-entries filed under no category at all. The check stayed while unmarked
 * settlements were still in the books; #122 and #123 removed the last of them, measured at
 * zero on 11.09.2026, and removing it moved the totals by 0.00 €.
 */
fun List<Booking>.excludeTransfers(): List<Booking> =
    filter { it.source != BookingSource.TRANSFER }

fun sumIncome(bookings: List<Booking>): Double =
    bookings.excludeTransfers().asSequence().map { it.amount }.filter { it > 0.0 }.sum()

/** Returns negative value (e.g. -123.45). */
fun sumExpenses(bookings: List<Booking>): Double =
    bookings.excludeTransfers().asSequence().map { it.amount }.filter { it < 0.0 }.sum()

/**
 * The three figures that belong together on the Auswertungen tab: what came in, what went
 * out, and the difference (#100).
 *
 * They live in one function because they drifted apart when they did not. Android computed
 * the result as income minus expense while the desktop summed every booking untouched, so
 * the third tile contradicted the two beside it by 3,111.10 € — exactly the Kontoabgleich
 * corrections and card settlements that the other two leave out. Two screens carrying two
 * copies of one rule is how that happens; this is the one copy.
 *
 * [income] and [expense] are both positive. [result] is income minus expense, which is what
 * the label "Periodenergebnis" promises: did more come in this period than went out.
 *
 * Corrections are always dropped — a Kontoabgleich only moves a balance to where it should
 * have been, it is not a flow. Transfers are dropped when [excludeTransfers] is set, which
 * the caller decides: across all accounts, and on a credit card, both halves would otherwise
 * be counted; in the single-account view of an ordinary account the settlement really is
 * money leaving.
 */
data class PeriodTotals(
    val income: Double,
    val expense: Double,
) {
    val result: Double get() = income - expense
}

fun periodTotals(bookings: List<Booking>, excludeTransfers: Boolean): PeriodTotals {
    val relevant = bookings
        .filter { it.source != BookingSource.RECONCILIATION }
        .let { if (excludeTransfers) it.excludeTransfers() else it }
    return PeriodTotals(
        income = relevant.filter { it.amount > 0.0 }.sumOf { it.amount },
        expense = relevant.filter { it.amount < 0.0 }.sumOf { -it.amount },
    )
}

fun netTotal(bookings: List<Booking>): Double =
    bookings.asSequence().map { it.amount }.sum()

fun topExpenseCategory(bookings: List<Booking>): String? =
    topExpenseCategoryWithAmount(bookings)?.first

/**
 * Returns (category, summedExpenseAmount). Expense amount is negative.
 * Example: ("Groceries", -300.0)
 */
fun topExpenseCategoryWithAmount(bookings: List<Booking>): Pair<String, Double>? {
    val expensesByCategory: Map<String, Double> =
        bookings.excludeTransfers().asSequence()
            .filter { it.amount < 0.0 }
            .mapNotNull { b ->
                val cat = b.category?.trim().orEmpty()
                if (cat.isBlank()) null else cat to b.amount
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, amounts) -> amounts.sum() }

    return expensesByCategory
        .minByOrNull { (_, sum) -> sum } // most negative = biggest spending
        ?.toPair()
}

/**
 * One row in the category spending breakdown.
 *
 * @param categoryName  Display name of the category (or "Ohne Kategorie" for uncategorized).
 * @param amount        Absolute spending amount (positive number, e.g. 42.50).
 * @param percentage    Share of total spending, 0.0–1.0.
 */
data class CategoryBreakdownEntry(
    val categoryName: String,
    val amount: Double,
    val percentage: Double
)

/**
 * Which half of the books a category breakdown is about.
 *
 * The breakdown answered "where did the money go" and could not be asked the other
 * question, although the data for it was already there: income carries categories
 * too, and "which of my sources brought how much" is the same shape of answer.
 */
enum class CategoryBreakdownSide { EXPENSES, INCOME }

/**
 * Calculates a per-category breakdown for the given bookings.
 *
 * [side] picks the half: expenses (amount < 0, the default) or income (amount > 0).
 * Bookings without a category are grouped under "Ohne Kategorie".
 * The result is sorted by amount descending (biggest first), and the amounts are
 * positive on both sides, so a caller can draw them without minding the sign.
 * Categories with nothing in the period are omitted.
 *
 * Pure function — no Android imports.
 */
fun calculateCategoryBreakdown(
    bookings: List<Booking>,
    filterTransfers: Boolean = true,
    side: CategoryBreakdownSide = CategoryBreakdownSide.EXPENSES,
): List<CategoryBreakdownEntry> {
    val relevant = (if (filterTransfers) bookings.excludeTransfers() else bookings)
        .filter { if (side == CategoryBreakdownSide.INCOME) it.amount > 0.0 else it.amount < 0.0 }
    if (relevant.isEmpty()) return emptyList()

    val total = relevant.sumOf { kotlin.math.abs(it.amount) }
    if (total == 0.0) return emptyList()

    return relevant
        .groupBy { it.category?.trim()?.takeIf { c -> c.isNotBlank() } ?: "Ohne Kategorie" }
        .map { (categoryName, categoryBookings) ->
            val absSum = categoryBookings.sumOf { kotlin.math.abs(it.amount) }
            CategoryBreakdownEntry(
                categoryName = categoryName,
                amount = absSum,
                percentage = absSum / total
            )
        }
        .sortedByDescending { it.amount }
}

/**
 * One data point for a monthly income vs expense bar chart.
 *
 * @param monthLabel  Display label, e.g. "Jan 26" or "Mär 26".
 * @param income      Total income (positive) for the month.
 * @param expense     Total absolute expense (positive) for the month.
 * @param fromMillis  First instant this point covers.
 * @param toMillisExclusive  First instant it no longer covers.
 *
 * The two instants are what makes a bar clickable (#114): a click has to narrow a list to
 * the period the bar stands for, and matching on [monthLabel] would mean parsing back a
 * string that was built for reading. They also work for the credit-card variant, where a
 * point is a billing cycle rather than a calendar month.
 */
data class MonthlyIncomeExpense(
    val monthLabel: String,
    val income: Double,
    val expense: Double,
    val fromMillis: Long,
    val toMillisExclusive: Long,
) {
    /** Whether [booking] falls into this point, by its own date as everywhere else. */
    fun covers(booking: Booking): Boolean {
        val ts = booking.effectiveDate ?: booking.timestamp
        return ts >= fromMillis && ts < toMillisExclusive
    }
}

/**
 * Calculates monthly income vs expense totals for the given bookings.
 *
 * Groups bookings by calendar month (using effectiveDate or timestamp),
 * and returns one entry per month that has at least one booking,
 * sorted chronologically.
 *
 * Pure function — no Android imports.
 */
fun calculateMonthlyIncomeExpense(
    bookings: List<Booking>,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    filterTransfers: Boolean = true,
): List<MonthlyIncomeExpense> {
    if (bookings.isEmpty()) return emptyList()

    val shortMonthName = { month: Int ->
        when (month) {
            1 -> "Jan"; 2 -> "Feb"; 3 -> "Mär"; 4 -> "Apr"
            5 -> "Mai"; 6 -> "Jun"; 7 -> "Jul"; 8 -> "Aug"
            9 -> "Sep"; 10 -> "Okt"; 11 -> "Nov"; 12 -> "Dez"
            else -> ""
        }
    }

    return (if (filterTransfers) bookings.excludeTransfers() else bookings)
        .groupBy { booking ->
            val ts = booking.effectiveDate ?: booking.timestamp
            val date = Instant.fromEpochMilliseconds(ts).toLocalDateTime(timeZone).date
            LocalDate(date.year, date.monthNumber, 1)
        }
        .toSortedMap()
        .map { (firstOfMonth, monthBookings) ->
            val income = monthBookings.filter { it.amount > 0.0 }.sumOf { it.amount }
            val expense = monthBookings.filter { it.amount < 0.0 }.sumOf { kotlin.math.abs(it.amount) }
            val label = "${shortMonthName(firstOfMonth.monthNumber)} ${firstOfMonth.year.toString().takeLast(2)}"
            val nextMonth =
                if (firstOfMonth.monthNumber == 12) LocalDate(firstOfMonth.year + 1, 1, 1)
                else LocalDate(firstOfMonth.year, firstOfMonth.monthNumber + 1, 1)
            MonthlyIncomeExpense(
                monthLabel = label,
                income = income,
                expense = expense,
                fromMillis = firstOfMonth.atStartOfDayIn(timeZone).toEpochMilliseconds(),
                toMillisExclusive = nextMonth.atStartOfDayIn(timeZone).toEpochMilliseconds(),
            )
        }
}

/**
 * One data point in a balance-over-time series.
 *
 * @param dateMillis  Start-of-day millis (local time zone) for the date.
 * @param balance     Running balance at end of this date.
 */
data class BalanceDataPoint(
    val dateMillis: Long,
    val balance: Double
)

/**
 * Calculates the running balance over time for the given bookings within a period.
 *
 * The algorithm:
 * 1. Starts with [startingBalance] (balance before the period).
 * 2. Groups bookings by their effective date (effectiveDate ?: timestamp), converted to start-of-day.
 * 3. Iterates day-by-day through the period, accumulating the balance.
 * 4. Returns one [BalanceDataPoint] per day that has at least one booking,
 *    plus today (if earlier bookings exist) so the chart ends at the present.
 * 5. Does NOT anchor a fictitious starting point at the period start if no bookings exist there.
 * 6. Never includes data points beyond today.
 *
 * Pure function — no Android imports.
 *
 * @param bookingsInPeriod  Bookings already filtered for the account and period (POSTED only).
 * @param startingBalance   Balance at the start of the period (before any bookings in the period).
 * @param periodFromMillis  Start of the period (inclusive), millis.
 * @param periodToMillis    End of the period (exclusive), millis.
 * @param timeZone          Time zone for date conversion.
 */
fun calculateBalanceOverTime(
    bookingsInPeriod: List<Booking>,
    startingBalance: Double,
    periodFromMillis: Long,
    periodToMillis: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): List<BalanceDataPoint> {
    if (periodFromMillis >= periodToMillis) return emptyList()

    // No bookings in period → no chart (avoids fictitious zero-point chart)
    if (bookingsInPeriod.isEmpty()) return emptyList()

    val startDate = Instant.fromEpochMilliseconds(periodFromMillis).toLocalDateTime(timeZone).date
    val rawEndDate = Instant.fromEpochMilliseconds(periodToMillis).toLocalDateTime(timeZone).date

    // Clip end date to tomorrow — never render future data points
    val today = Clock.System.todayIn(timeZone)
    val endDate = if (rawEndDate > today.plus(1, DateTimeUnit.DAY)) today.plus(1, DateTimeUnit.DAY) else rawEndDate

    if (startDate >= endDate) return emptyList()

    // Group bookings by their effective date (start-of-day)
    val bookingsByDate: Map<LocalDate, List<Booking>> = bookingsInPeriod
        .groupBy { booking ->
            val ts = booking.effectiveDate ?: booking.timestamp
            Instant.fromEpochMilliseconds(ts).toLocalDateTime(timeZone).date
        }

    // Find the first and last date with actual bookings to define chart range
    val datesWithBookings = bookingsByDate.keys.sorted()
    val firstBookingDate = datesWithBookings.first()
    val lastBookingDate = maxOf(
        datesWithBookings.last(),
        minOf(today, endDate.minus(1, DateTimeUnit.DAY))
    )

    val points = mutableListOf<BalanceDataPoint>()
    var runningBalance = startingBalance
    var currentDate = startDate

    // Accumulate balance for days before the first booking date (not emitted)
    while (currentDate < firstBookingDate) {
        val dayBookings = bookingsByDate[currentDate]
        if (dayBookings != null) {
            runningBalance += dayBookings.sumOf { it.amount }
        }
        currentDate = currentDate.plus(1, DateTimeUnit.DAY)
    }

    // Emit a data point for every day from first booking to last relevant date
    while (currentDate <= lastBookingDate && currentDate < endDate) {
        val dayBookings = bookingsByDate[currentDate]
        if (dayBookings != null) {
            runningBalance += dayBookings.sumOf { it.amount }
        }
        val dayMillis = currentDate.atStartOfDayIn(timeZone).toEpochMilliseconds()
        points.add(BalanceDataPoint(dateMillis = dayMillis, balance = runningBalance))
        currentDate = currentDate.plus(1, DateTimeUnit.DAY)
    }

    return points
}
