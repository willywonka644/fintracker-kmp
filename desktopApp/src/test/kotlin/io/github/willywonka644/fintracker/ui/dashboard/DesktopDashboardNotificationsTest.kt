package io.github.willywonka644.fintracker.ui.dashboard

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.AccountType
import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.ui.theme.FinTrackerTheme
import kotlinx.datetime.Clock
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * #98 — the over-limit notification on the desktop dashboard.
 *
 * Four separate faults are covered here, all of which the old code had: it
 * announced every credit card that merely *had* a limit, it summarised the
 * affected accounts into a count instead of naming them, its row led nowhere,
 * and its badge added one for "some account is over" however many were.
 *
 * Bookings are stamped **now** on purpose: a credit card's balance is summed over
 * `currentBillingCyclePeriod`, and the present instant is inside the current cycle
 * for every billing start day and every time zone. They are also pre-verified, so
 * they raise no "Ungeprüfte Buchungen" entry and the panel stays isolated to the
 * one notification under test.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopDashboardNotificationsTest {

    @get:Rule
    val compose = createComposeRule()

    /** Set by the dashboard's onAccountClick callback. */
    private var openedAccount: Account? = null

    private fun card(id: String, name: String, limit: Double) = Account(
        id = id,
        name = name,
        type = AccountType.CREDIT_CARD,
        billingStartDay = 18,
        spendingLimit = limit,
    )

    private fun spend(id: String, accountId: String, amount: Double) = Booking(
        id = id,
        accountId = accountId,
        amount = -amount,
        description = "Einkauf $id",
        timestamp = Clock.System.now().toEpochMilliseconds(),
        isVerified = true,
    )

    /** Renders without opening the panel — the badge is on the closed dashboard. */
    private fun showDashboardClosed(accounts: List<Account>, bookings: List<Booking>) {
        compose.setContent {
            FinTrackerTheme {
                DesktopDashboard(
                    accounts = accounts,
                    bookings = bookings,
                    categories = emptyList(),
                    onAccountClick = { openedAccount = it },
                    onAccountSaved = {},
                    onAccountDeleted = {},
                )
            }
        }
    }

    /** The same dashboard with the notification panel opened. */
    private fun showDashboard(accounts: List<Account>, bookings: List<Booking>) {
        showDashboardClosed(accounts, bookings)
        compose.onNodeWithTag(DesktopDashboardTestTags.NOTIFICATIONS_BUTTON).performClick()
    }

    @Test
    fun aCardThatOnlyHasALimitIsNotReportedAsOverIt() {
        val visa = card("acc-1", "Visa", limit = 500.0)
        showDashboard(listOf(visa), listOf(spend("b1", visa.id, 100.0)))

        compose.onNodeWithText("Keine Benachrichtigungen").assertExists()
        compose.onNodeWithTag(DesktopDashboardTestTags.limitWarning(visa.id)).assertDoesNotExist()
    }

    @Test
    fun spendingExactlyTheLimitIsNearlyThereAndNotYetOverIt() {
        val visa = card("acc-1", "Visa", limit = 500.0)
        showDashboard(listOf(visa), listOf(spend("b1", visa.id, 500.0)))

        // Since #99 this is worth saying — but it is not yet "überschritten":
        // computeLimitUsage flags that only above the limit, not on it.
        compose.onNodeWithTag(DesktopDashboardTestTags.limitWarning(visa.id))
            .assertTextContains("Limit fast erreicht")
    }

    @Test
    fun aCardAtNinetyPercentWarnsBeforeTheLimitIsGone() {
        val visa = card("acc-1", "Visa", limit = 500.0)
        showDashboard(listOf(visa), listOf(spend("b1", visa.id, 450.0)))

        compose.onNodeWithTag(DesktopDashboardTestTags.limitWarning(visa.id))
            .assertTextContains("Limit fast erreicht")
            .assertTextContains("Visa")
    }

    @Test
    fun aGirokontoBeyondItsDispoIsReportedInItsOwnWords() {
        // The account this could not say anything about until #99: not a card, deepest
        // in the red, and no limit was even storable for it.
        val giro = Account(
            id = "acc-giro",
            name = "Girokonto",
            type = AccountType.GIRO,
            spendingLimit = 3000.0,
        )
        showDashboard(listOf(giro), listOf(spend("b1", giro.id, 3500.0)))

        compose.onNodeWithTag(DesktopDashboardTestTags.limitWarning(giro.id))
            .assertTextContains("Dispo überzogen")
            .assertTextContains("Girokonto")
    }

    @Test
    fun anExceededLimitNamesTheAccount() {
        val visa = card("acc-1", "Visa", limit = 500.0)
        showDashboard(listOf(visa), listOf(spend("b1", visa.id, 600.0)))

        compose.onNodeWithTag(DesktopDashboardTestTags.limitWarning(visa.id))
            .assertTextContains("Limit überschritten")
            .assertTextContains("Visa")
    }

    @Test
    fun clickingTheNotificationOpensThatAccount() {
        val visa = card("acc-1", "Visa", limit = 500.0)
        showDashboard(listOf(visa), listOf(spend("b1", visa.id, 600.0)))
        assertNull(openedAccount, "nothing should be open before the click")

        compose.onNodeWithTag(DesktopDashboardTestTags.limitWarning(visa.id)).performClick()

        assertEquals(visa.id, openedAccount?.id)
    }

    @Test
    fun everyAffectedAccountGetsItsOwnRow() {
        val visa = card("acc-1", "Visa", limit = 500.0)
        val amex = card("acc-2", "Amex", limit = 300.0)
        val master = card("acc-3", "Mastercard", limit = 1000.0)
        showDashboard(
            listOf(visa, amex, master),
            listOf(
                spend("b1", visa.id, 600.0),
                spend("b2", amex.id, 400.0),
                spend("b3", master.id, 50.0),
            ),
        )

        compose.onAllNodesWithText("Limit überschritten").assertCountEquals(2)
        compose.onNodeWithTag(DesktopDashboardTestTags.limitWarning(visa.id)).assertTextContains("Visa")
        compose.onNodeWithTag(DesktopDashboardTestTags.limitWarning(amex.id)).assertTextContains("Amex")
        compose.onNodeWithTag(DesktopDashboardTestTags.limitWarning(master.id)).assertDoesNotExist()
    }

    // ── Badge ────────────────────────────────────────────────────────────────
    //
    // The badge used to add 1 for "some account is over its limit", however many
    // were. Since the panel lists one row per account, the badge has to count
    // rows or it contradicts what opening it shows.

    @Test
    fun theBadgeCountsOneRowPerOverLimitAccount() {
        val visa = card("acc-1", "Visa", limit = 500.0)
        val amex = card("acc-2", "Amex", limit = 300.0)
        showDashboardClosed(
            listOf(visa, amex),
            listOf(spend("b1", visa.id, 600.0), spend("b2", amex.id, 400.0)),
        )

        // The old code said "1" here, for the same two rows in the panel.
        compose.onNodeWithTag(DesktopDashboardTestTags.NOTIFICATION_BADGE)
            .assertTextEquals("2")
    }

    @Test
    fun theBadgeAddsOverLimitAccountsToTheOtherNotifications() {
        val visa = card("acc-1", "Visa", limit = 500.0)
        val unverified = spend("b1", visa.id, 600.0).copy(isVerified = false)
        showDashboardClosed(listOf(visa), listOf(unverified))

        // One unaudited-bookings entry plus one over-limit account.
        compose.onNodeWithTag(DesktopDashboardTestTags.NOTIFICATION_BADGE)
            .assertTextEquals("2")
    }

    @Test
    fun thereIsNoBadgeWhenNothingIsWorthReporting() {
        val visa = card("acc-1", "Visa", limit = 500.0)
        showDashboardClosed(listOf(visa), listOf(spend("b1", visa.id, 100.0)))

        compose.onNodeWithTag(DesktopDashboardTestTags.NOTIFICATION_BADGE)
            .assertDoesNotExist()
    }
}
