package io.github.willywonka644.fintracker.ui.reconciliation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.BookingSource
import io.github.willywonka644.fintracker.BookingStatus
import io.github.willywonka644.fintracker.ui.theme.FinTrackerTheme
import io.github.willywonka644.fintracker.util.MoneyFormat
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Phase 6.5 Tier 2a — reconciliation dialog.
 *
 * The reconciliation *math* is already covered by `ReconciliationServiceTest`
 * (Tier 1b). What these tests add is the UI wiring the service tests cannot see:
 * that the dialog feeds the right bookings into the service, renders the result,
 * parses German decimal input, and only calls back when a correction is actually
 * needed.
 *
 * Timestamps are built at noon on a long-past date in the system time zone —
 * the dialog resolves its own cutoff via `TimeZone.currentSystemDefault()` and
 * defaults the Stichtag to today, so a 2020 booking is inside the window in
 * every time zone.
 */
@OptIn(ExperimentalTestApi::class)
class ReconciliationDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private val account = "acc-1"
    private val tz = TimeZone.currentSystemDefault()

    /** Set by the dialog's onCreateCorrectionBooking callback. */
    private var created: Booking? = null

    private fun pastMillis(date: LocalDate = LocalDate(2020, 1, 15)): Long =
        LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, 12, 0)
            .toInstant(tz)
            .toEpochMilliseconds()

    private fun booking(
        id: String,
        amount: Double,
        accountId: String = account,
        status: BookingStatus = BookingStatus.POSTED,
    ) = Booking(
        id = id,
        accountId = accountId,
        amount = amount,
        description = id,
        timestamp = pastMillis(),
        status = status,
        source = BookingSource.MANUAL,
    )

    /**
     * Expected balance of [account] is 1000 - 750 = 250. The other two bookings
     * must not count: one belongs to a different account, one is only scheduled.
     */
    private fun bookings() = listOf(
        booking("b1", 1000.0),
        booking("b2", -750.0),
        booking("b3", 500.0, accountId = "acc-2"),
        booking("b4", 9999.0, status = BookingStatus.SCHEDULED),
    )

    private fun showDialog(allBookings: List<Booking> = bookings()) {
        compose.setContent {
            FinTrackerTheme {
                ReconciliationDialog(
                    accountId = account,
                    accountName = "Girokonto",
                    allBookings = allBookings,
                    nextBookingId = { "correction-1" },
                    onDismiss = {},
                    onCreateCorrectionBooking = { created = it },
                )
            }
        }
    }

    @Test
    fun expectedBalanceCountsOnlyPostedBookingsOfThisAccount() {
        showDialog()

        compose.onNodeWithTag(ReconciliationTestTags.EXPECTED_BALANCE)
            .assertTextContains(MoneyFormat.currency(250.0), substring = true)
    }

    @Test
    fun differenceCreatesCorrectionBookingAndAcceptsGermanDecimalComma() {
        showDialog()

        // Click first so the field holds focus before typing, as a real user would.
        compose.onNodeWithTag(ReconciliationTestTags.ACTUAL_INPUT).performClick()
        compose.onNodeWithTag(ReconciliationTestTags.ACTUAL_INPUT).performTextInput("312,50")
        compose.onNodeWithTag(ReconciliationTestTags.SUBMIT).performClick()

        val correction = assertNotNull(created, "expected a correction booking")
        assertEquals(62.5, correction.amount, 0.001, "actual 312,50 minus expected 250,00")
        assertEquals(account, correction.accountId)
        assertEquals("correction-1", correction.id, "id must come from nextBookingId")

        compose.onNodeWithTag(ReconciliationTestTags.RESULT_MESSAGE)
            .assertTextContains(MoneyFormat.currencySigned(62.5), substring = true)
    }

    @Test
    fun matchingBalanceCreatesNoCorrection() {
        showDialog()

        compose.onNodeWithTag(ReconciliationTestTags.ACTUAL_INPUT).performClick()
        compose.onNodeWithTag(ReconciliationTestTags.ACTUAL_INPUT).performTextInput("250,00")
        compose.onNodeWithTag(ReconciliationTestTags.SUBMIT).performClick()

        assertNull(created, "no correction booking when the balance already matches")
        compose.onNodeWithTag(ReconciliationTestTags.RESULT_MESSAGE)
            .assertTextContains("Kein Unterschied", substring = true)
    }

    @Test
    fun emptyAmountIsRejectedWithoutCreatingABooking() {
        showDialog()

        compose.onNodeWithTag(ReconciliationTestTags.SUBMIT).performClick()

        assertNull(created, "clicking with an empty amount must not book anything")
        compose.onNodeWithTag(ReconciliationTestTags.RESULT_MESSAGE)
            .assertTextContains("Ungültiger Betrag", substring = true)
    }
}
