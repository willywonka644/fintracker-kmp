package io.github.willywonka644.fintracker.ui.booking

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.AccountType
import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.BookingSource
import io.github.willywonka644.fintracker.ui.theme.FinTrackerTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * #114 — booking a transfer on the desktop, the counterpart to #121 on Android.
 *
 * The arithmetic itself is covered by TransferFactoryTest in shared; what these check is
 * the wiring: that the tile only appears where a transfer is actually possible, and that
 * confirming produces two mirrored halves rather than one lonely booking.
 */
@OptIn(ExperimentalTestApi::class)
class BookingFormTransferTest {

    @get:Rule
    val compose = createComposeRule()

    private val giro = Account(id = "giro", name = "Girokonto", type = AccountType.GIRO)
    private val karte = Account(id = "karte", name = "MasterCard", type = AccountType.CREDIT_CARD)

    private var saved: Booking? = null
    private var transfer: Pair<Booking, Booking>? = null

    private fun showDialog(
        accounts: List<Account> = listOf(giro, karte),
        existing: Booking? = null,
        allowTransfer: Boolean = true,
    ) {
        saved = null
        transfer = null
        compose.setContent {
            FinTrackerTheme {
                BookingFormDialog(
                    accountId = "giro",
                    categories = emptyList(),
                    existing = existing,
                    accounts = accounts,
                    onDismiss = {},
                    onSave = { saved = it },
                    onSaveTransfer = if (allowTransfer) { a, b -> transfer = a to b } else null,
                )
            }
        }
    }

    @Test
    fun theTileIsOfferedWhenATransferIsPossible() {
        showDialog()

        compose.onNodeWithText("Umbuchung").assertExists()
    }

    @Test
    fun thereIsNoTileWithoutASecondAccount() {
        // Nowhere to transfer to — offering it would be a dead end.
        showDialog(accounts = listOf(giro))

        compose.onNodeWithText("Umbuchung").assertDoesNotExist()
    }

    @Test
    fun thereIsNoTileWhenTheCallerDoesNotHandleTransfers() {
        showDialog(allowTransfer = false)

        compose.onNodeWithText("Umbuchung").assertDoesNotExist()
    }

    @Test
    fun anExistingBookingCannotBeTurnedIntoATransfer() {
        // It would leave the other half missing; on Android the same case locks the tiles.
        showDialog(
            existing = Booking(
                id = "b1",
                accountId = "giro",
                amount = -50.0,
                description = "Lebensmittel",
                timestamp = 1_600_000_000_000,
            )
        )

        compose.onNodeWithText("Umbuchung").assertDoesNotExist()
    }

    @Test
    fun pickingTheTileSwapsTheCategoryBlockForAnAccountAndASecondDate() {
        showDialog()

        compose.onNodeWithText("Umbuchung").performClick()

        compose.onNodeWithText("Auf welches Konto").assertExists()
        compose.onNodeWithText("Datum Zugang").assertExists()
        compose.onNodeWithText("Datum Abgang").assertExists()
    }

    @Test
    fun savingWithoutADestinationIsRefusedAndNothingIsWritten() {
        showDialog()
        compose.onNodeWithText("Umbuchung").performClick()
        compose.onNodeWithTag(BookingFormTestTags.AMOUNT).performTextInput("400")
        compose.onNodeWithTag(BookingFormTestTags.DESCRIPTION).performTextInput("Kartenabrechnung")

        compose.onNodeWithTag(BookingFormTestTags.SAVE).performScrollTo().performClick()

        assertNull(transfer, "a transfer without a destination must not be written")
        assertNull(saved)
    }

    @Test
    fun confirmingWritesTwoMirroredHalvesAndNoOrdinaryBooking() {
        showDialog()
        compose.onNodeWithText("Umbuchung").performClick()
        compose.onNodeWithTag(BookingFormTestTags.AMOUNT).performTextInput("400")
        compose.onNodeWithTag(BookingFormTestTags.DESCRIPTION).performTextInput("Kartenabrechnung")
        compose.onNodeWithText("Konto wählen").performClick()
        compose.onNodeWithText("MasterCard").performClick()

        compose.onNodeWithTag(BookingFormTestTags.SAVE).performScrollTo().performClick()

        val pair = assertNotNull(transfer, "both halves should have been handed over")
        val (outgoing, incoming) = pair
        assertEquals(-400.0, outgoing.amount)
        assertEquals(400.0, incoming.amount)
        assertEquals("giro", outgoing.accountId)
        assertEquals("karte", incoming.accountId)
        assertEquals(outgoing.transferGroupId, incoming.transferGroupId)
        assertNotNull(outgoing.transferGroupId)
        assertEquals(BookingSource.TRANSFER, outgoing.source)
        assertEquals(BookingSource.TRANSFER, incoming.source)
        // No category, so there is no name anyone could rename to switch the handling off.
        assertNull(outgoing.category)
        assertNull(incoming.category)
        assertNull(saved, "the ordinary save path must not fire as well")
    }

    @Test
    fun theSourceAccountIsNotOfferedAsItsOwnDestination() {
        showDialog()
        compose.onNodeWithText("Umbuchung").performClick()

        compose.onNodeWithText("Konto wählen").performClick()

        compose.onNodeWithText("Girokonto").assertDoesNotExist()
        compose.onNodeWithText("MasterCard").assertExists()
    }
}
