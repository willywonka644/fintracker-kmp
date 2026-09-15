package io.github.willywonka644.fintracker.analytics

import io.github.willywonka644.fintracker.Booking
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime

/**
 * Which period a screen is showing — the one answer for all of them (#114).
 *
 * There were four private enums for this, one per screen, and they had drifted: the same
 * span was called "90 Tage" on one and "Letzte 90 Tage" on another, the Auswertungen
 * offered "30 Tage" where the Buchungen offered 90, and only one of the four could be
 * cleared back to "no filter". Four copies of a concept is how that happens.
 *
 * `null` — the absence of a choice — means the whole range. There is deliberately no
 * ALL_TIME member: offering it as a choice as well would be two ways of saying the same
 * thing, and the one that reads better is "nothing is selected".
 */
sealed interface PeriodChoice {

    /** The label a chip or menu entry shows. */
    val label: String

    /**
     * Spans relative to today. These are habits — "what has been going on lately" — and
     * belong on one click, which is why the screens show them as chips.
     */
    enum class Relative(override val label: String, val days: Int?) : PeriodChoice {
        DAYS_30("30 Tage", 30),
        DAYS_90("90 Tage", 90),
        THIS_MONTH("Dieser Monat", null),
        MONTHS_6("6 Monate", 180),
        THIS_YEAR("Dieses Jahr", null),
    }

    /** A calendar quarter. [quarter] is 1..4. */
    data class Quarter(val year: Int, val quarter: Int) : PeriodChoice {
        override val label: String get() = "Q$quarter $year"
    }

    /** A whole calendar year. */
    data class Year(val year: Int) : PeriodChoice {
        override val label: String get() = "$year"
    }

    /** A range the user typed. Both ends inclusive, as the date fields present them. */
    data class Custom(val from: LocalDate, val to: LocalDate) : PeriodChoice {
        override val label: String get() = "Benutzerdefiniert"
    }
}

/**
 * The filter this choice stands for; `null` means the whole range.
 *
 * [today] is a parameter so the result can be pinned in a test rather than depending on
 * the day it runs.
 */
fun PeriodChoice?.toFilter(
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    today: LocalDate = Clock.System.todayIn(timeZone),
): PeriodFilter = when (this) {
    null -> allTimePeriod()
    is PeriodChoice.Relative -> when (this) {
        // today durchreichen, nicht die Systemuhr nehmen: sonst haengt das Ergebnis am
        // Tag des Laufs und laesst sich nicht festnageln.
        PeriodChoice.Relative.THIS_MONTH ->
            currentMonthFilter(today.atStartOfDayIn(timeZone).toEpochMilliseconds(), timeZone)
        PeriodChoice.Relative.THIS_YEAR -> customRangeFilter(LocalDate(today.year, 1, 1), today, timeZone)
        // The span includes today, so it reaches back days - 1.
        else -> customRangeFilter(today.minus(days!! - 1, DateTimeUnit.DAY), today, timeZone)
    }
    is PeriodChoice.Quarter -> {
        val firstMonth = (quarter - 1) * 3 + 1
        val from = LocalDate(year, firstMonth, 1)
        val to = if (quarter == 4) LocalDate(year, 12, 31) else LocalDate(year, firstMonth + 3, 1).minus(1, DateTimeUnit.DAY)
        customRangeFilter(from, to, timeZone)
    }
    is PeriodChoice.Year -> customRangeFilter(LocalDate(year, 1, 1), LocalDate(year, 12, 31), timeZone)
    is PeriodChoice.Custom -> customRangeFilter(from, to, timeZone)
}

/**
 * The quarters and years that actually contain bookings, newest first.
 *
 * Built from the data rather than from a range of years, so the menu never offers an empty
 * period — and never needs a scroll through years nobody has used. Deleted rows are left
 * out; scheduled ones are not, because a period one has already planned into is a period
 * worth being able to look at.
 */
fun availableAbsolutePeriods(
    bookings: List<Booking>,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): List<PeriodChoice> {
    val dates = bookings.asSequence()
        .filter { !it.deleted }
        .map { Instant.fromEpochMilliseconds(it.effectiveDate ?: it.timestamp).toLocalDateTime(timeZone).date }
        .toList()
    if (dates.isEmpty()) return emptyList()

    val quarters = dates
        .map { PeriodChoice.Quarter(it.year, (it.monthNumber - 1) / 3 + 1) }
        .distinct()
        .sortedWith(compareByDescending<PeriodChoice.Quarter> { it.year }.thenByDescending { it.quarter })
    val years = dates.map { it.year }.distinct().sortedDescending().map { PeriodChoice.Year(it) }

    return quarters + years
}
