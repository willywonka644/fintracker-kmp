package io.github.willywonka644.fintracker

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import io.github.willywonka644.fintracker.ui.booking.BookingFormTestTags
import io.github.willywonka644.fintracker.ui.booking.BookingListTestTags
import io.github.willywonka644.fintracker.ui.recurring.DauerauftraegeTestTags
import io.github.willywonka644.fintracker.ui.recurring.RecurringRuleDialogTestTags
import io.github.willywonka644.fintracker.ui.shell.DesktopNavDestination
import io.github.willywonka644.fintracker.ui.shell.DesktopShellTestTags
import io.github.willywonka644.fintracker.ui.theme.FinTrackerTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Phase 6.5 Tier 2b — the whole screen against a real database.
 *
 * Unlike the Tier 2a tests, which hand each screen a ready-made list, this
 * renders [AppContent] on top of [TestServices] and lets it load its own data.
 * That covers the wiring the component tests cannot see: that the screen reads
 * through the repositories at all, and that it does so without reaching for the
 * production database.
 *
 * [AppContent] loads on a background dispatcher, so assertions wait via
 * `waitUntil` rather than expecting data to be there on the first frame.
 */
@OptIn(ExperimentalTestApi::class)
class AppContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val services = TestServices()
    private val tz = TimeZone.currentSystemDefault()

    private fun pastMillis(date: LocalDate = LocalDate(2020, 1, 15)): Long =
        LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, 12, 0)
            .toInstant(tz)
            .toEpochMilliseconds()

    /** Renders the app shell, holding the selected account like `main` does. */
    private fun showApp() {
        compose.setContent {
            var selected by remember { mutableStateOf<Account?>(null) }
            FinTrackerTheme {
                AppContent(
                    services = services,
                    selectedAccount = selected,
                    onSelectedAccountChange = { selected = it },
                    showCategories = false,
                    onHideCategories = {},
                    pendingCsvImportText = null,
                    onCsvImportDismiss = {},
                    showRestoreConfirm = false,
                    pendingRestoreJson = null,
                    onRestoreDismiss = {},
                    showPinSettings = false,
                    onPinSettingsDismiss = {},
                    showReconciliation = false,
                    onReconciliationDismiss = {},
                    showAudit = false,
                    onAuditDismiss = {},
                    showWiFiSync = false,
                    onWiFiSyncDismiss = {},
                )
            }
        }
    }

    private fun waitForText(text: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun theDashboardShowsAccountsItLoadedFromTheDatabase() {
        services.accountRepository.saveAccounts(
            listOf(
                Account(id = "acc-1", name = "Girokonto", type = AccountType.GIRO),
                Account(id = "acc-2", name = "Sparkonto", type = AccountType.SPARKONTO),
            )
        )
        services.bookingRepository.saveBookings(
            listOf(
                Booking(
                    id = "b1",
                    accountId = "acc-1",
                    amount = 2000.0,
                    description = "Gehalt",
                    timestamp = pastMillis(),
                )
            )
        )

        showApp()

        // Nothing was handed in as a parameter — AppContent had to go through
        // the repositories to know these exist.
        waitForText("Girokonto")
        waitForText("Sparkonto")
    }

    @Test
    fun aBookingEnteredInTheUiIsPersistedAndAppearsInTheList() {
        services.accountRepository.saveAccounts(
            listOf(Account(id = "acc-1", name = "Girokonto", type = AccountType.GIRO))
        )

        showApp()
        waitForText("Girokonto")

        // Sidebar → Buchungen, then pick the account so the add button appears.
        compose.onNodeWithTag(DesktopShellTestTags.nav(DesktopNavDestination.BUCHUNGEN)).performClick()
        compose.onNodeWithTag(BookingListTestTags.ACCOUNT_HEADER).performClick()
        compose.onNodeWithTag(BookingListTestTags.accountOption("acc-1")).performClick()
        compose.onNodeWithTag(BookingListTestTags.ADD_BUTTON).performClick()

        // Typed with a German decimal comma on purpose: the numpad produces one,
        // and the field used to swallow it without a trace.
        compose.onNodeWithTag(BookingFormTestTags.AMOUNT).performClick()
        compose.onNodeWithTag(BookingFormTestTags.AMOUNT).performTextInput("49,90")
        compose.onNodeWithTag(BookingFormTestTags.DESCRIPTION).performClick()
        compose.onNodeWithTag(BookingFormTestTags.DESCRIPTION).performTextInput("Wocheneinkauf")
        compose.onNodeWithTag(BookingFormTestTags.SAVE).performScrollTo().performClick()

        // It reached the database...
        compose.waitUntil(timeoutMillis = 5_000) {
            services.bookingRepository.loadBookings().any { it.description == "Wocheneinkauf" }
        }
        val saved = services.bookingRepository.loadBookings().single { it.description == "Wocheneinkauf" }
        assertEquals(-49.90, saved.amount, 0.001, "the form defaults to Ausgabe, so the amount is negative")
        assertEquals("acc-1", saved.accountId)
        assertEquals(BookingStatus.POSTED, saved.status, "a booking dated today is posted, not scheduled")

        // ...and the list reloaded to show it.
        waitForText("Wocheneinkauf")
    }

    @Test
    fun aNewRuleGetsAnIdAssignedAndAppearsInTheList() {
        services.accountRepository.saveAccounts(
            listOf(Account(id = "acc-1", name = "Girokonto", type = AccountType.GIRO))
        )

        showApp()
        waitForText("Girokonto")

        compose.onNodeWithTag(DesktopShellTestTags.nav(DesktopNavDestination.DAUERAUFTRAEGE)).performClick()
        compose.onNodeWithTag(DauerauftraegeTestTags.ADD_FAB).performClick()

        compose.onNodeWithTag(RecurringRuleDialogTestTags.AMOUNT).performClick()
        compose.onNodeWithTag(RecurringRuleDialogTestTags.AMOUNT).performTextInput("-49,90")
        compose.onNodeWithTag(RecurringRuleDialogTestTags.DESCRIPTION).performClick()
        compose.onNodeWithTag(RecurringRuleDialogTestTags.DESCRIPTION).performTextInput("Fitnessstudio")
        compose.onNodeWithTag(RecurringRuleDialogTestTags.SAVE).performClick()

        compose.waitUntil(timeoutMillis = 5_000) {
            services.recurringRuleRepository.loadRules().any { it.description == "Fitnessstudio" }
        }
        val saved = services.recurringRuleRepository.loadRules().single { it.description == "Fitnessstudio" }
        // The dialog emits a blank id; AppContent numbers it. Counting tombstones
        // while doing so is what stops a new rule from colliding with a deleted one.
        assertEquals("r1", saved.id, "AppContent assigns the id, the dialog leaves it blank")
        assertEquals(-49.9, saved.amount, 0.001)
        assertEquals("acc-1", saved.accountId, "defaults to the only account")

        waitForText("Fitnessstudio")
    }
}
