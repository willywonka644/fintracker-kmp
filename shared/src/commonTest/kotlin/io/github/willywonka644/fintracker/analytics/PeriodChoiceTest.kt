package io.github.willywonka644.fintracker.analytics

import io.github.willywonka644.fintracker.Booking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The one period type all four screens use (#114).
 *
 * They each had their own enum before, and the copies had drifted — same span, different
 * label; different spans, same screen role; and only one of the four could be cleared.
 */
class PeriodChoiceTest {

    private val tz = TimeZone.UTC
    private val today = LocalDate(2026, 9, 11)

    private fun at(y: Int, m: Int, d: Int) =
        LocalDate(y, m, d).atStartOfDayIn(tz).toEpochMilliseconds()

    private fun booking(id: String, y: Int, m: Int, d: Int, deleted: Boolean = false) = Booking(
        id = id,
        accountId = "giro",
        amount = -10.0,
        description = "test",
        timestamp = at(y, m, d),
        effectiveDate = at(y, m, d),
        deleted = deleted,
    )

    @Test
    fun noChoiceMeansTheWholeRange() {
        // The absence of a selection is the "everything" state — there is no ALL_TIME
        // member on purpose, because that would be the same thing said twice.
        assertEquals(PeriodType.ALL_TIME, null.toFilter(tz, today).type)
    }

    @Test
    fun aRelativeSpanReachesBackFromTodayAndIncludesIt() {
        val filter = PeriodChoice.Relative.DAYS_30.toFilter(tz, today)

        // 30 days including today means 13.08. through 11.09., not 12.08.
        assertEquals(at(2026, 8, 13), filter.fromInclusive)
        assertTrue(filter.toExclusive!! > at(2026, 9, 11))
    }

    @Test
    fun thisMonthStartsOnTheFirst() {
        val filter = PeriodChoice.Relative.THIS_MONTH.toFilter(tz, today)

        // Gegen den gesetzten Tag geprüft, nicht gegen die Systemuhr — genau das ging
        // vorher schief, weil currentMonthFilter seinen eigenen Clock-Default nahm.
        assertEquals(at(2026, 9, 1), filter.fromInclusive)
        assertEquals(at(2026, 10, 1), filter.toExclusive)
    }

    @Test
    fun thisYearStartsInJanuary() {
        val filter = PeriodChoice.Relative.THIS_YEAR.toFilter(tz, today)

        assertEquals(at(2026, 1, 1), filter.fromInclusive)
    }

    @Test
    fun everyQuarterCoversItsThreeMonths() {
        assertEquals(at(2026, 1, 1), PeriodChoice.Quarter(2026, 1).toFilter(tz, today).fromInclusive)
        assertEquals(at(2026, 4, 1), PeriodChoice.Quarter(2026, 2).toFilter(tz, today).fromInclusive)
        assertEquals(at(2026, 7, 1), PeriodChoice.Quarter(2026, 3).toFilter(tz, today).fromInclusive)
        assertEquals(at(2026, 10, 1), PeriodChoice.Quarter(2026, 4).toFilter(tz, today).fromInclusive)
    }

    @Test
    fun theFourthQuarterEndsWithTheYearRatherThanRunningIntoTheNext() {
        // The one that would be off by a month if the last quarter were built like the others.
        val q4 = PeriodChoice.Quarter(2026, 4).toFilter(tz, today)

        assertTrue(q4.toExclusive!! > at(2026, 12, 31), "31.12. has to be inside the quarter")
        assertTrue(q4.toExclusive!! <= at(2027, 1, 2), "and 02.01. outside it")
    }

    @Test
    fun aYearCoversJanuaryToDecember() {
        val filter = PeriodChoice.Year(2025).toFilter(tz, today)

        assertEquals(at(2025, 1, 1), filter.fromInclusive)
        assertTrue(filter.toExclusive!! > at(2025, 12, 31))
        assertTrue(filter.toExclusive!! <= at(2026, 1, 2))
    }

    @Test
    fun labelsAreBuiltFromTheChoiceRatherThanStoredTwice() {
        assertEquals("Q3 2026", PeriodChoice.Quarter(2026, 3).label)
        assertEquals("2025", PeriodChoice.Year(2025).label)
        assertEquals("30 Tage", PeriodChoice.Relative.DAYS_30.label)
    }

    @Test
    fun onlyPeriodsThatHoldBookingsAreOffered() {
        // The point of building the menu from the data: no empty quarters, and no scroll
        // through years nobody has used.
        val offered = availableAbsolutePeriods(
            listOf(booking("a", 2026, 2, 5), booking("b", 2026, 8, 20)),
            tz,
        )

        assertEquals(
            listOf("Q3 2026", "Q1 2026", "2026"),
            offered.map { it.label },
        )
    }

    @Test
    fun theNewestPeriodComesFirst() {
        val offered = availableAbsolutePeriods(
            listOf(booking("old", 2025, 3, 1), booking("new", 2026, 9, 1)),
            tz,
        )

        assertEquals("Q3 2026", offered.first().label)
        assertEquals(listOf("2026", "2025"), offered.filterIsInstance<PeriodChoice.Year>().map { it.label })
    }

    @Test
    fun deletedBookingsDoNotOpenUpAPeriod() {
        val offered = availableAbsolutePeriods(
            listOf(booking("live", 2026, 9, 1), booking("gone", 2024, 5, 1, deleted = true)),
            tz,
        )

        assertTrue(offered.none { it.label == "2024" }, "a tombstone must not offer its year")
    }

    @Test
    fun anEmptyDatabaseOffersNothingRatherThanTheCurrentYear() {
        assertTrue(availableAbsolutePeriods(emptyList(), tz).isEmpty())
    }

    @Test
    fun aCustomRangeKeepsBothEndsTheUserTyped() {
        val filter = PeriodChoice.Custom(LocalDate(2026, 3, 4), LocalDate(2026, 3, 9)).toFilter(tz, today)

        assertEquals(at(2026, 3, 4), filter.fromInclusive)
        assertTrue(filter.toExclusive!! > at(2026, 3, 9), "the last day has to be inside the range")
    }
}
