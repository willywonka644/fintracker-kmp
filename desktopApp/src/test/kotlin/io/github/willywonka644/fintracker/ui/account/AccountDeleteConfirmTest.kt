package io.github.willywonka644.fintracker.ui.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.AccountType
import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.Frequency
import io.github.willywonka644.fintracker.RecurringRule
import io.github.willywonka644.fintracker.ui.theme.FinTrackerTheme
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * #114 — deleting an account on the desktop says what it takes, and has to be acknowledged.
 *
 * Two things were wrong before. The screen carrying this dialog was not wired to anything,
 * so an account could not be deleted on the desktop at all; and the dialog itself asked
 * "und alle Buchungen löschen?" without a number, for the most destructive action in the
 * app — one whose cascade propagates to every device as tombstones.
 */
@OptIn(ExperimentalTestApi::class)
class AccountDeleteConfirmTest {

    @get:Rule
    val compose = createComposeRule()

    private val giro = Account(id = "acc-1", name = "Girokonto", type = AccountType.GIRO)

    private fun booking(id: String) = Booking(
        id = id,
        accountId = "acc-1",
        amount = -10.0,
        description = "test",
        timestamp = 1_600_000_000_000,
    )

    private val rule = RecurringRule(
        id = "r1",
        amount = -50.0,
        description = "Miete",
        accountId = "acc-1",
        frequency = Frequency.MONTHLY,
        nextExecutionDate = LocalDate(2026, 10, 1),
    )

    private var deleted: Account? = null

    private fun showScreen(bookings: List<Booking>, rules: List<RecurringRule>) {
        deleted = null
        compose.setContent {
            FinTrackerTheme {
                // Innerhalb der 1024x768 des Testhosts bleiben: der Menueknopf sitzt rechts
                // im Panel, und ausserhalb der Compose-Grenzen laesst sich nicht klicken.
                Box(Modifier.requiredSize(1000.dp, 700.dp)) {
                    AccountOverviewScreen(
                        accounts = listOf(giro),
                        bookings = bookings,
                        recurringRules = rules,
                        selectedAccount = giro,
                        onAccountSelected = {},
                        onAccountSaved = {},
                        onAccountDeleted = { deleted = it },
                        detailContent = { _, _ -> },
                    )
                }
            }
        }
        // Das Menue oeffnen und "Löschen" waehlen. Danach traegt nur noch der Dialogknopf
        // diesen Text, weil das Menue sich beim Klick schliesst.
        compose.onNodeWithContentDescription("Account options").performClick()
        compose.onNodeWithText("Löschen").performClick()
    }

    @Test
    fun theDialogNamesWhatGoesWithTheAccount() {
        showScreen(listOf(booking("b1"), booking("b2"), booking("b3")), listOf(rule))

        // Die Zahl steht zweimal da, und das ist Absicht: einmal im Satz, einmal im
        // Kaestchen. Deshalb auf die beiden vollstaendigen Formulierungen pruefen statt
        // auf "3 Buchungen" allein, das mehrdeutig waere.
        compose.onNodeWithText("mit 3 Buchungen und 1 Dauerauftrag gelöscht", substring = true)
            .assertExists()
        compose.onNodeWithText("dass 3 Buchungen und 1 Dauerauftrag mitgelöscht werden", substring = true)
            .assertExists()
    }

    @Test
    fun theDeleteButtonStaysLockedUntilTheBoxIsTicked() {
        showScreen(listOf(booking("b1")), emptyList())

        compose.onNodeWithText("Löschen").assertIsNotEnabled()
        compose.onNodeWithText("Mir ist klar", substring = true).performClick()
        compose.onNodeWithText("Löschen").assertIsEnabled()
    }

    @Test
    fun nothingIsDeletedWhileTheBoxIsUnticked() {
        showScreen(listOf(booking("b1")), emptyList())

        compose.onNodeWithText("Löschen").performClick()

        assertNull(deleted, "the account must not go before the box is ticked")
    }

    @Test
    fun tickingTheBoxAndConfirmingDeletes() {
        showScreen(listOf(booking("b1")), emptyList())

        compose.onNodeWithText("Mir ist klar", substring = true).performClick()
        compose.onNodeWithText("Löschen").performClick()

        assertEquals("acc-1", deleted?.id)
    }

    @Test
    fun anEmptyAccountNeedsNoAcknowledgement() {
        // The checkbox is a hurdle for a loss; with nothing to lose it would be theatre.
        showScreen(emptyList(), emptyList())

        compose.onNodeWithText("keine Buchungen", substring = true).assertExists()
        compose.onNodeWithText("Löschen").assertIsEnabled()
    }

    @Test
    fun cancellingLeavesTheAccountAlone() {
        showScreen(listOf(booking("b1")), emptyList())

        compose.onNodeWithText("Abbrechen").performClick()

        assertNull(deleted)
    }
}
