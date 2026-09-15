package io.github.willywonka644.fintracker.analytics

import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.Frequency
import io.github.willywonka644.fintracker.RecurringRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

class RecurringOccurrencesTest {

    private val timeZone = TimeZone.of("Europe/Berlin")
    private val today = LocalDate(2026, 3, 15)

    // -- advanceDate --

    @Test
    fun advanceDate_weekly_addsOneWeek() {
        val date = LocalDate(2026, 3, 1)
        assertEquals(LocalDate(2026, 3, 8), advanceDate(date, Frequency.WEEKLY))
    }

    @Test
    fun advanceDate_monthly_addsOneMonth() {
        val date = LocalDate(2026, 3, 1)
        assertEquals(LocalDate(2026, 4, 1), advanceDate(date, Frequency.MONTHLY))
    }

    @Test
    fun advanceDate_yearly_addsOneYear() {
        val date = LocalDate(2026, 3, 1)
        assertEquals(LocalDate(2027, 3, 1), advanceDate(date, Frequency.YEARLY))
    }

    // -- generateFutureOccurrences --

    @Test
    fun generateFutureOccurrences_monthlyUnlimited_generates6MonthsOfOccurrences() {
        val rule = createRule(
            nextExecutionDate = LocalDate(2026, 4, 1),
            frequency = Frequency.MONTHLY,
            remainingExecutions = null
        )

        val occurrences = generateFutureOccurrences(rule, today = today, horizonMonths = 6, timeZone = timeZone)

        // From April 1 to September 1 = 6 occurrences
        assertEquals(6, occurrences.size)
        assertTrue(occurrences.all { it.isPlanned })
        assertEquals(LocalDate(2026, 4, 1), occurrences[0].occurrenceDate)
        assertEquals(LocalDate(2026, 9, 1), occurrences[5].occurrenceDate)
    }

    @Test
    fun generateFutureOccurrences_limitedExecutions_stopsAtLimit() {
        val rule = createRule(
            nextExecutionDate = LocalDate(2026, 4, 1),
            frequency = Frequency.MONTHLY,
            remainingExecutions = 3
        )

        val occurrences = generateFutureOccurrences(rule, today = today, horizonMonths = 12, timeZone = timeZone)

        assertEquals(3, occurrences.size)
    }

    @Test
    fun generateFutureOccurrences_skipsExcludedDates() {
        val rule = createRule(
            nextExecutionDate = LocalDate(2026, 4, 1),
            frequency = Frequency.MONTHLY,
            excludedDates = setOf(LocalDate(2026, 5, 1))
        )

        val occurrences = generateFutureOccurrences(rule, today = today, horizonMonths = 6, timeZone = timeZone)

        // May 1 excluded, so we get Apr, Jun, Jul, Aug, Sep = 5
        assertEquals(5, occurrences.size)
        assertTrue(occurrences.none { it.occurrenceDate == LocalDate(2026, 5, 1) })
    }

    @Test
    fun generateFutureOccurrences_pastDatesSkipped() {
        // Rule starts in the past; past dates should be skipped
        val rule = createRule(
            nextExecutionDate = LocalDate(2026, 1, 1),
            frequency = Frequency.MONTHLY
        )

        val occurrences = generateFutureOccurrences(rule, today = today, horizonMonths = 6, timeZone = timeZone)

        // Should only include dates after March 15: April 1 through Sep 1 = 6
        assertTrue(occurrences.all { it.occurrenceDate!! > today })
    }

    @Test
    fun generateFutureOccurrences_weeklyRule_generatesCorrectly() {
        val rule = createRule(
            nextExecutionDate = LocalDate(2026, 3, 22), // First future Sunday after today (Mar 15)
            frequency = Frequency.WEEKLY,
            remainingExecutions = 4
        )

        val occurrences = generateFutureOccurrences(rule, today = today, horizonMonths = 6, timeZone = timeZone)

        assertEquals(4, occurrences.size)
        assertEquals(LocalDate(2026, 3, 22), occurrences[0].occurrenceDate)
        assertEquals(LocalDate(2026, 3, 29), occurrences[1].occurrenceDate)
    }

    @Test
    fun generateFutureOccurrences_allOccurrencesHaveCorrectBookingFields() {
        val rule = createRule(
            id = "r5",
            amount = -50.0,
            description = "Netflix",
            category = "Entertainment",
            nextExecutionDate = LocalDate(2026, 4, 1),
            frequency = Frequency.MONTHLY,
            remainingExecutions = 2
        )

        val occurrences = generateFutureOccurrences(rule, today = today, timeZone = timeZone)

        for (occ in occurrences) {
            assertEquals("r5", occ.booking.recurringRuleId)
            assertEquals(-50.0, occ.booking.amount)
            assertEquals("Netflix", occ.booking.description)
            assertEquals("Entertainment", occ.booking.category)
            assertTrue(occ.isPlanned)
        }
    }

    // -- autoPostDueBookings --

    @Test
    fun autoPostDueBookings_noRulesDue_returnsEmpty() {
        val rule = createRule(nextExecutionDate = LocalDate(2026, 4, 1))

        val result = autoPostDueBookings(
            rules = listOf(rule),
            existingBookings = emptyList(),
            today = today,
            idProvider = idSequence(),
            timeZone = timeZone
        )

        assertTrue(result.newBookings.isEmpty())
        assertTrue(result.updatedRules.isEmpty())
        assertTrue(result.exhaustedRuleIds.isEmpty())
    }

    @Test
    fun autoPostDueBookings_oneDueDate_createsOneBooking() {
        val rule = createRule(
            nextExecutionDate = LocalDate(2026, 3, 15), // today
            frequency = Frequency.MONTHLY
        )

        val result = autoPostDueBookings(
            rules = listOf(rule),
            existingBookings = emptyList(),
            today = today,
            idProvider = idSequence(),
            timeZone = timeZone
        )

        assertEquals(1, result.newBookings.size)
        assertEquals(1, result.updatedRules.size)
        assertEquals(LocalDate(2026, 4, 15), result.updatedRules[0].nextExecutionDate)
    }

    @Test
    fun autoPostDueBookings_multipleDueDates_createsMultipleBookings() {
        // Rule was due Jan 1, Feb 1, Mar 1 — all in the past
        val rule = createRule(
            nextExecutionDate = LocalDate(2026, 1, 1),
            frequency = Frequency.MONTHLY
        )

        val result = autoPostDueBookings(
            rules = listOf(rule),
            existingBookings = emptyList(),
            today = today,
            idProvider = idSequence(),
            timeZone = timeZone
        )

        assertEquals(3, result.newBookings.size) // Jan, Feb, Mar
        assertEquals(LocalDate(2026, 4, 1), result.updatedRules[0].nextExecutionDate)
    }

    @Test
    fun autoPostDueBookings_alreadyPosted_doesNotDuplicate() {
        val rule = createRule(
            id = "r1",
            nextExecutionDate = LocalDate(2026, 3, 1),
            frequency = Frequency.MONTHLY
        )

        // Already have a booking for Mar 1 with this rule ID
        val existingBooking = Booking(
            id = "existing1",
            accountId = "a1",
            amount = -50.0,
            description = "Test",
            timestamp = LocalDate(2026, 3, 1).atStartOfDayIn(timeZone).toEpochMilliseconds(),
            recurringRuleId = "r1"
        )

        val result = autoPostDueBookings(
            rules = listOf(rule),
            existingBookings = listOf(existingBooking),
            today = today,
            idProvider = idSequence(),
            timeZone = timeZone
        )

        // Should only create bookings for dates without existing bookings
        // Mar 1 already exists, Mar 15 is today but that's not a scheduled date
        assertEquals(0, result.newBookings.size)
    }

    @Test
    fun autoPostDueBookings_exhaustedRule_addedToExhaustedSet() {
        val rule = createRule(
            nextExecutionDate = LocalDate(2026, 3, 1),
            frequency = Frequency.MONTHLY,
            remainingExecutions = 1
        )

        val result = autoPostDueBookings(
            rules = listOf(rule),
            existingBookings = emptyList(),
            today = today,
            idProvider = idSequence(),
            timeZone = timeZone
        )

        assertEquals(1, result.newBookings.size)
        assertTrue(result.exhaustedRuleIds.contains(rule.id))
    }

    @Test
    fun autoPostDueBookings_skipsExcludedDates() {
        val rule = createRule(
            nextExecutionDate = LocalDate(2026, 2, 1),
            frequency = Frequency.MONTHLY,
            excludedDates = setOf(LocalDate(2026, 2, 1))
        )

        val result = autoPostDueBookings(
            rules = listOf(rule),
            existingBookings = emptyList(),
            today = today,
            idProvider = idSequence(),
            timeZone = timeZone
        )

        // Feb 1 excluded, so only Mar 1 should be posted
        assertEquals(1, result.newBookings.size)
    }

    // -- Helpers --

    private fun idSequence(): () -> String {
        var counter = 1
        return { "gen_${counter++}" }
    }

    private fun createRule(
        id: String = "r1",
        amount: Double = -50.0,
        description: String = "Test",
        accountId: String = "a1",
        frequency: Frequency = Frequency.MONTHLY,
        nextExecutionDate: LocalDate = LocalDate(2026, 3, 1),
        remainingExecutions: Int? = null,
        category: String? = null,
        excludedDates: Set<LocalDate> = emptySet()
    ) = RecurringRule(
        id = id,
        amount = amount,
        description = description,
        accountId = accountId,
        frequency = frequency,
        nextExecutionDate = nextExecutionDate,
        remainingExecutions = remainingExecutions,
        category = category,
        excludedDates = excludedDates
    )
}
