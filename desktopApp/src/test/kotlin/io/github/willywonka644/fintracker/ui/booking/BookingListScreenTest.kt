package io.github.willywonka644.fintracker.ui.booking

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.AccountType
import io.github.willywonka644.fintracker.Booking
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Phase 6.5 Tier 2a — booking list screen.
 *
 * Note that this screen does **not** filter by account itself; the caller passes
 * a pre-filtered list (see `AppContent` in Main.kt). The `account` parameter only
 * drives the header, the balance and the credit-card billing bar. The tests
 * mirror that contract.
 */
@OptIn(ExperimentalTestApi::class)
class BookingListScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val tz = TimeZone.currentSystemDefault()

    private val giro = Account(id = "acc-1", name = "Girokonto", type = AccountType.GIRO)
    private val savings = Account(id = "acc-2", name = "Sparkonto", type = AccountType.SPARKONTO)

    private var addClicked = false
    private var openedBooking: Booking? = null
    private var editedBooking: Booking? = null
    private var selectedAccount: Account? = null
    private var accountSelectionCount = 0

    private fun pastMillis(date: LocalDate = LocalDate(2020, 1, 15)): Long =
        LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, 12, 0)
            .toInstant(tz)
            .toEpochMilliseconds()

    private fun booking(
        id: String,
        description: String,
        amount: Double,
        accountId: String = giro.id,
        status: BookingStatus = BookingStatus.POSTED,
        date: LocalDate = LocalDate(2020, 1, 15),
    ) = Booking(
        id = id,
        accountId = accountId,
        amount = amount,
        description = description,
        timestamp = pastMillis(date),
        status = status,
    )

    private fun showScreen(
        bookings: List<Booking>,
        account: Account? = giro,
        accounts: List<Account> = listOf(giro, savings),
    ) {
        compose.setContent {
            FinTrackerTheme {
                BookingListScreen(
                    bookings = bookings,
                    categories = emptyList(),
                    onAddClick = { addClicked = true },
                    onEditBooking = { editedBooking = it },
                    onDeleteBooking = {},
                    onOpenBooking = { openedBooking = it },
                    account = account,
                    accounts = accounts,
                    onAccountSelected = { selectedAccount = it; accountSelectionCount++ },
                )
            }
        }
    }

    @Test
    fun scheduledBookingsAreHiddenUntilTheStatusFilterIsChanged() {
        showScreen(
            listOf(
                booking("b1", "Miete", -800.0),
                booking("b2", "Geplante Rate", -50.0, status = BookingStatus.SCHEDULED),
            )
        )

        compose.onNodeWithText("Miete").assertIsDisplayed()
        compose.onNodeWithText("Geplante Rate").assertDoesNotExist()
    }

    @Test
    fun balanceSumsOnlyPostedBookingsOfTheSelectedAccount() {
        showScreen(
            listOf(
                booking("b1", "Gehalt", 2000.0),
                booking("b2", "Miete", -800.0),
                booking("b3", "Geplante Rate", -50.0, status = BookingStatus.SCHEDULED),
                booking("b4", "Fremdbuchung", -10.0, accountId = savings.id),
            )
        )

        // 2000 - 800; the scheduled booking and the other account must not count.
        compose.onNodeWithTag(BookingListTestTags.BALANCE)
            .assertTextContains(MoneyFormat.currency(1200.0), substring = true)
    }

    @Test
    fun addButtonIsShownForASelectedAccountAndReportsClicks() {
        showScreen(listOf(booking("b1", "Miete", -800.0)))

        compose.onNodeWithTag(BookingListTestTags.ADD_BUTTON).performClick()

        assertTrue(addClicked, "onAddClick must fire")
    }

    @Test
    fun addButtonIsHiddenWhenNoAccountIsSelected() {
        showScreen(listOf(booking("b1", "Miete", -800.0)), account = null)

        // Without a target account there is nothing to book into.
        compose.onNodeWithTag(BookingListTestTags.ADD_BUTTON).assertDoesNotExist()
    }

    @Test
    fun pickingAnAccountFromTheHeaderDropdownReportsIt() {
        showScreen(listOf(booking("b1", "Miete", -800.0)), account = null)

        compose.onNodeWithTag(BookingListTestTags.ACCOUNT_HEADER).performClick()
        compose.onNodeWithTag(BookingListTestTags.accountOption(giro.id)).performClick()

        assertEquals(giro, selectedAccount)
        assertEquals(1, accountSelectionCount)
    }

    @Test
    fun rowsCarryTheAccountNameWhenNoAccountIsSelected() {
        showScreen(listOf(booking("b1", "Miete", -800.0)), account = null)

        // The account column only appears in "Alle Konten" mode — the header
        // reads "Alle Konten" here, so this node is the row's account cell.
        compose.onNodeWithText("Girokonto").assertIsDisplayed()
    }

    @Test
    fun resettingToAllAccountsReportsNull() {
        showScreen(listOf(booking("b1", "Miete", -800.0)), account = giro)

        compose.onNodeWithTag(BookingListTestTags.ACCOUNT_HEADER).performClick()
        compose.onNodeWithTag(BookingListTestTags.ACCOUNT_OPTION_ALL).performClick()

        assertNull(selectedAccount)
        assertEquals(1, accountSelectionCount)
    }

    @Test
    fun anAccountWithoutBookingsIsNotBlamedOnTheFilters() {
        showScreen(emptyList())

        // The status filter defaults to POSTED; on its own that must not make
        // the screen claim the filters hid something.
        compose.onNodeWithTag(BookingListTestTags.EMPTY_STATE)
            .assertTextContains("Noch keine Buchungen", substring = true)
    }

    @Test
    fun eachDayIsHeadedOnceAndCarriesTheTotalOfItsRows() {
        showScreen(
            listOf(
                booking("b1", "Miete", -800.0),
                booking("b2", "Strom", -200.0),
                booking("b3", "Gehalt", 2000.0, date = LocalDate(2020, 2, 20)),
            )
        )

        // 15.01.2020 was a Wednesday, 20.02.2020 a Thursday.
        compose.onNodeWithText("Mittwoch, 15. Januar 2020").assertIsDisplayed()
        compose.onNodeWithText("Donnerstag, 20. Februar 2020").assertIsDisplayed()

        // The total of the two rows below the first heading. No row carries this
        // figure, and the account balance is +1.000,00 €, so the node is unambiguous.
        compose.onNodeWithText(MoneyFormat.currency(-1000.0)).assertIsDisplayed()
    }

    @Test
    fun aPostedBookingDoesNotRepeatItsStatusInEveryRow() {
        showScreen(
            listOf(
                booking("b1", "Miete", -800.0),
                booking("b2", "Strom", -200.0),
            )
        )

        // "Gebucht" is left exactly once — on the status filter button, where it is
        // a choice. In the rows it was the same sentence 580 times over (#125).
        compose.onAllNodesWithText("Gebucht").assertCountEquals(1)
    }

    @Test
    fun aScheduledBookingStillSaysSo() {
        showScreen(listOf(booking("b1", "Geplante Rate", -50.0, status = BookingStatus.SCHEDULED)))

        // Status filter to "Geplant" — the default hides scheduled bookings.
        compose.onNodeWithText("Gebucht").performClick()
        compose.onNodeWithText("Geplant").performClick()

        // Once on the filter button, once as the pill in the row.
        compose.onAllNodesWithText("Geplant").assertCountEquals(2)
    }

    @Test
    fun theRowActionsOnlyAppearUnderThePointer() {
        showScreen(listOf(booking("b1", "Miete", -800.0)))

        // Two links that are almost never used but were read in every one of 588
        // rows (#125). Off the row they are not there at all.
        compose.onNodeWithText("Bearb.").assertDoesNotExist()
        compose.onNodeWithText("Lösch.").assertDoesNotExist()

        compose.onNodeWithText("Miete").performMouseInput { moveTo(center) }

        compose.onNodeWithText("Bearb.").assertIsDisplayed()
        compose.onNodeWithText("Lösch.").assertIsDisplayed()
    }

    @Test
    fun clickingARowOpensTheBookingAndNotTheForm() {
        showScreen(listOf(booking("b1", "Miete", -800.0)))

        compose.onNodeWithText("Miete").performClick()

        // Nachsehen ist kein Schreibvorgang (#129): der Klick fuehrt in die Ansicht,
        // das Formular bleibt zu.
        assertEquals("b1", openedBooking?.id)
        assertNull(editedBooking)
    }

    @Test
    fun narrowingTheDateRangeBlamesTheFilters() {
        // The booking is dated 2020, so restricting to the current month hides it.
        showScreen(listOf(booking("b1", "Miete", -800.0)))

        compose.onNodeWithText("Dieser Monat").performClick()

        compose.onNodeWithTag(BookingListTestTags.EMPTY_STATE)
            .assertTextContains("Keine Buchungen entsprechen den aktuellen Filtern.", substring = true)
    }
}
