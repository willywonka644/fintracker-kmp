package io.github.willywonka644.fintracker.ui.booking

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.BookingSource
import io.github.willywonka644.fintracker.ui.theme.FinTrackerTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The view that stands between a click and the edit form (#129).
 *
 * What it must not do is claim anything the data does not hold — so the tests pin the
 * two places where that was tempting: the value date only appears when it differs from
 * the booking date, and a transfer names the account holding its other half.
 */
@OptIn(ExperimentalTestApi::class)
class BookingDetailDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private val tz = TimeZone.currentSystemDefault()

    private fun millis(date: LocalDate, hour: Int = 12): Long =
        LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, hour, 0)
            .toInstant(tz)
            .toEpochMilliseconds()

    private fun booking(
        amount: Double = -42.5,
        description: String = "Wocheneinkauf",
        category: String? = "Lebensmittel",
        effectiveDate: Long? = null,
        source: BookingSource = BookingSource.MANUAL,
        transferGroupId: String? = null,
        lastModifiedAt: Long = 0L,
    ) = Booking(
        id = "b1",
        accountId = "acc-1",
        amount = amount,
        description = description,
        timestamp = millis(LocalDate(2026, 9, 4)),
        category = category,
        effectiveDate = effectiveDate,
        source = source,
        transferGroupId = transferGroupId,
        lastModifiedAt = lastModifiedAt,
    )

    private var edited = 0
    private var deleted = 0
    private var dismissed = 0

    private fun show(
        booking: Booking,
        counterpartAccountName: String? = null,
    ) {
        compose.setContent {
            FinTrackerTheme {
                BookingDetailDialog(
                    booking = booking,
                    accountName = "Girokonto",
                    counterpartAccountName = counterpartAccountName,
                    categoryName = booking.category.orEmpty(),
                    onEdit = { edited++ },
                    onDelete = { deleted++ },
                    onDismiss = { dismissed++ },
                )
            }
        }
    }

    @Test
    fun itNamesWhatTheBookingHolds() {
        show(booking())

        compose.onNodeWithText("Wocheneinkauf").assertIsDisplayed()
        compose.onNodeWithText("Girokonto").assertIsDisplayed()
        compose.onNodeWithText("Lebensmittel").assertIsDisplayed()
        compose.onNodeWithText("Freitag, 4. September 2026").assertIsDisplayed()
        compose.onNodeWithText("Gebucht").assertIsDisplayed()
    }

    @Test
    fun aValueDateOnTheSameDayIsNotRepeated() {
        show(booking(effectiveDate = millis(LocalDate(2026, 9, 4), hour = 8)))

        compose.onNodeWithText("Wertstellung").assertDoesNotExist()
    }

    @Test
    fun aValueDateOnAnotherDayIsSaid() {
        // Genau die Auskunft, wegen der man nachsieht — und die Ursache der vier
        // Buchungen aus #123, die im falschen Abrechnungszeitraum landeten.
        show(booking(effectiveDate = millis(LocalDate(2026, 9, 8))))

        compose.onNodeWithText("Wertstellung").assertIsDisplayed()
        compose.onNodeWithText("Dienstag, 8. September 2026").assertIsDisplayed()
    }

    @Test
    fun aTransferSaysWhereItsOtherHalfIs() {
        show(
            booking(source = BookingSource.TRANSFER, transferGroupId = "g1"),
            counterpartAccountName = "Sparkonto",
        )

        compose.onNodeWithText("Umbuchung").assertIsDisplayed()
        compose.onNodeWithText("Gegenkonto").assertIsDisplayed()
        compose.onNodeWithText("Sparkonto").assertIsDisplayed()
    }

    @Test
    fun aBookingWithoutAChangeStampDoesNotInventOne() {
        // lastModifiedAt = 0 heisst "nie gesyncht", nicht "am 01.01.1970 geändert".
        show(booking(lastModifiedAt = 0L))

        compose.onNodeWithText("Zuletzt geändert").assertDoesNotExist()
    }

    @Test
    fun editingAndDeletingAreTwoDeliberateSteps() {
        show(booking())

        compose.onNodeWithText("Bearbeiten").performClick()
        compose.onNodeWithText("Löschen").performClick()
        compose.onNodeWithText("Schließen").performClick()

        assertEquals(1, edited)
        assertEquals(1, deleted)
        assertTrue(dismissed >= 1, "Schließen meldet sich")
    }
}
