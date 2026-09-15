package io.github.willywonka644.fintracker.analytics

import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.BookingStatus
import io.github.willywonka644.fintracker.Frequency
import io.github.willywonka644.fintracker.RecurringRule
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime

/**
 * Wraps a Booking for display in the booking list.
 * [isPlanned] = true means it's a future scheduled occurrence (not yet persisted).
 * [occurrenceDate] is the LocalDate of the recurring occurrence (for exclusion tracking).
 */
data class DisplayBooking(
    val booking: Booking,
    val isPlanned: Boolean = false,
    val occurrenceDate: LocalDate? = null
)

/**
 * Advances a date by one interval of the given [frequency].
 */
fun advanceDate(date: LocalDate, frequency: Frequency): LocalDate =
    when (frequency) {
        Frequency.WEEKLY -> date.plus(1, DateTimeUnit.WEEK)
        Frequency.MONTHLY -> date.plus(1, DateTimeUnit.MONTH)
        Frequency.YEARLY -> date.plus(1, DateTimeUnit.YEAR)
    }

/**
 * Generates future planned occurrences from a recurring rule.
 *
 * Only generates occurrences AFTER [today]. Past/today occurrences should be
 * auto-posted as real bookings instead.
 *
 * For unlimited rules: generates up to [horizonMonths] months into the future.
 * For limited rules: generates all remaining occurrences within the horizon.
 *
 * Excluded dates (from [RecurringRule.excludedDates]) are skipped.
 */
fun generateFutureOccurrences(
    rule: RecurringRule,
    today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
    horizonMonths: Int = 6,
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): List<DisplayBooking> {
    val horizon = today.plus(horizonMonths, DateTimeUnit.MONTH)
    val occurrences = mutableListOf<DisplayBooking>()

    var currentDate = rule.nextExecutionDate
    var remaining = rule.remainingExecutions

    // Skip past dates (they should already be auto-posted)
    while (currentDate <= today && (remaining == null || remaining > 0)) {
        if (currentDate !in rule.excludedDates) {
            remaining = remaining?.minus(1)
        }
        currentDate = advanceDate(currentDate, rule.frequency)
    }

    // Generate future occurrences
    while (currentDate <= horizon && (remaining == null || remaining > 0)) {
        if (currentDate !in rule.excludedDates) {
            val timestamp = currentDate.atStartOfDayIn(timeZone).toEpochMilliseconds()
            val booking = Booking(
                id = "planned_${rule.id}_$currentDate",
                accountId = rule.accountId,
                amount = rule.amount,
                description = rule.description,
                timestamp = timestamp,
                category = rule.category,
                recurringRuleId = rule.id
            )
            occurrences.add(
                DisplayBooking(
                    booking = booking,
                    isPlanned = true,
                    occurrenceDate = currentDate
                )
            )
            remaining = remaining?.minus(1)
        }
        currentDate = advanceDate(currentDate, rule.frequency)
    }

    return occurrences
}

/**
 * Processes due recurring rules: returns bookings that should be auto-posted
 * (past/today occurrences) and the updated rules with advanced nextExecutionDate.
 *
 * Returns a pair of:
 * - List of new Booking objects to persist
 * - List of updated RecurringRule objects (with advanced dates)
 * - List of rule IDs to remove (exhausted rules)
 */
data class AutoPostResult(
    val newBookings: List<Booking>,
    val updatedRules: List<RecurringRule>,
    val exhaustedRuleIds: Set<String>
)

fun autoPostDueBookings(
    rules: List<RecurringRule>,
    existingBookings: List<Booking>,
    today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
    horizonMonths: Int = 6,
    idProvider: () -> String,
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): AutoPostResult {
    val newBookings = mutableListOf<Booking>()
    val updatedRules = mutableListOf<RecurringRule>()
    val exhaustedRuleIds = mutableSetOf<String>()
    val horizon = today.plus(horizonMonths, DateTimeUnit.MONTH)

    for (rule in rules) {
        val initialRemaining = rule.remainingExecutions
        if (initialRemaining != null && initialRemaining <= 0) {
            exhaustedRuleIds.add(rule.id)
            continue
        }

        var currentDate = rule.nextExecutionDate
        var remaining = rule.remainingExecutions
        var ruleChanged = false

        // Determine the end boundary: limited rules generate all remaining,
        // unlimited rules generate up to the horizon
        val endBoundary = if (remaining != null) null else today

        while ((remaining == null || remaining > 0) &&
            (endBoundary == null || currentDate <= endBoundary)
        ) {
            // Limited rules have no horizon boundary; they stop when remaining hits 0
            if (remaining != null && remaining <= 0) break

            if (currentDate !in rule.excludedDates) {
                // Check if this occurrence already exists (by recurringRuleId + date match)
                val alreadyExists = existingBookings.any { b ->
                    b.recurringRuleId == rule.id && isSameDay(b.timestamp, currentDate, timeZone)
                }

                if (!alreadyExists) {
                    val timestamp = currentDate.atStartOfDayIn(timeZone).toEpochMilliseconds()
                    val status = if (currentDate > today) {
                        BookingStatus.SCHEDULED
                    } else {
                        BookingStatus.POSTED
                    }
                    val booking = Booking(
                        id = idProvider(),
                        accountId = rule.accountId,
                        amount = rule.amount,
                        description = rule.description,
                        timestamp = timestamp,
                        category = rule.category,
                        recurringRuleId = rule.id,
                        status = status,
                        lastModifiedAt = Clock.System.now().toEpochMilliseconds()
                    )
                    newBookings.add(booking)
                    ruleChanged = true
                }
                remaining = remaining?.minus(1)
            }
            currentDate = advanceDate(currentDate, rule.frequency)
        }

        if (remaining != null && remaining <= 0) {
            exhaustedRuleIds.add(rule.id)
        } else if (ruleChanged || currentDate != rule.nextExecutionDate) {
            updatedRules.add(
                rule.copy(
                    nextExecutionDate = currentDate,
                    remainingExecutions = remaining,
                    lastModifiedAt = Clock.System.now().toEpochMilliseconds()
                )
            )
        }
    }

    return AutoPostResult(
        newBookings = newBookings,
        updatedRules = updatedRules,
        exhaustedRuleIds = exhaustedRuleIds
    )
}

/**
 * Result of generating initial bookings when a recurring rule is first created.
 */
data class InitialBookingsResult(
    val bookings: List<Booking>,
    val updatedRule: RecurringRule
)

/**
 * Generates all initial bookings for a newly created recurring rule.
 *
 * For limited rules (remainingExecutions != null): generates ALL occurrences upfront.
 * For unlimited rules: generates occurrences up to [horizonMonths] months into the future.
 *
 * Each occurrence gets the appropriate status:
 * - Date <= today → POSTED
 * - Date > today → SCHEDULED
 *
 * Returns the generated bookings and the updated rule with advanced nextExecutionDate
 * and decremented remainingExecutions.
 */
fun generateInitialBookings(
    rule: RecurringRule,
    today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
    horizonMonths: Int = 6,
    idProvider: () -> String,
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): InitialBookingsResult {
    val bookings = mutableListOf<Booking>()
    var currentDate = rule.nextExecutionDate
    var remaining = rule.remainingExecutions
    val horizon = today.plus(horizonMonths, DateTimeUnit.MONTH)

    if (remaining != null) {
        // Limited rule: generate ALL occurrences upfront
        while (remaining > 0) {
            if (currentDate !in rule.excludedDates) {
                val timestamp = currentDate.atStartOfDayIn(timeZone).toEpochMilliseconds()
                val status = if (currentDate > today) {
                    BookingStatus.SCHEDULED
                } else {
                    BookingStatus.POSTED
                }
                bookings.add(
                    Booking(
                        id = idProvider(),
                        accountId = rule.accountId,
                        amount = rule.amount,
                        description = rule.description,
                        timestamp = timestamp,
                        category = rule.category,
                        recurringRuleId = rule.id,
                        status = status,
                        lastModifiedAt = Clock.System.now().toEpochMilliseconds()
                    )
                )
                remaining -= 1
            }
            currentDate = advanceDate(currentDate, rule.frequency)
        }
    } else {
        // Unlimited rule: generate up to horizon (POSTED for today/past, SCHEDULED for future)
        while (currentDate <= horizon) {
            if (currentDate !in rule.excludedDates) {
                val timestamp = currentDate.atStartOfDayIn(timeZone).toEpochMilliseconds()
                val status = if (currentDate > today) {
                    BookingStatus.SCHEDULED
                } else {
                    BookingStatus.POSTED
                }
                bookings.add(
                    Booking(
                        id = idProvider(),
                        accountId = rule.accountId,
                        amount = rule.amount,
                        description = rule.description,
                        timestamp = timestamp,
                        category = rule.category,
                        recurringRuleId = rule.id,
                        status = status,
                        lastModifiedAt = Clock.System.now().toEpochMilliseconds()
                    )
                )
            }
            currentDate = advanceDate(currentDate, rule.frequency)
        }
    }

    val updatedRule = rule.copy(
        nextExecutionDate = currentDate,
        remainingExecutions = remaining,
        lastModifiedAt = Clock.System.now().toEpochMilliseconds()
    )

    return InitialBookingsResult(
        bookings = bookings,
        updatedRule = updatedRule
    )
}

/**
 * Checks if a timestamp falls on the given LocalDate.
 */
private fun isSameDay(timestampMillis: Long, date: LocalDate, timeZone: TimeZone): Boolean {
    val bookingDate = Instant.fromEpochMilliseconds(timestampMillis)
        .toLocalDateTime(timeZone)
        .date
    return bookingDate == date
}
