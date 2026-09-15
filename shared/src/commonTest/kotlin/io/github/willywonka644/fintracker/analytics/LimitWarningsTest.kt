package io.github.willywonka644.fintracker.analytics

import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.AccountType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Limit and overdraft warnings (#99).
 *
 * The two decisions this pins down: a current account gets its own wording rather than
 * borrowing the card's, and the warning arrives at 90% instead of only once the limit
 * is already gone.
 *
 * Figures here are made up. The real balances that turned this up stay in the issue.
 */
class LimitWarningsTest {

    private fun account(
        id: String = "acc",
        name: String = "Konto",
        type: AccountType = AccountType.GIRO,
        limit: Double? = null,
    ) = Account(id = id, name = name, type = type, spendingLimit = limit)

    // ── the account that could not carry a limit at all ────────────────────────

    @Test
    fun giroBeyondItsOverdraft_warnsInItsOwnWords() {
        val giro = account(type = AccountType.GIRO, limit = 3000.0)
        val warning = limitWarningFor(giro, balance = -3500.0)

        assertEquals(LimitKind.OVERDRAFT, warning?.kind)
        assertEquals(LimitSeverity.OVER, warning?.severity)
        assertEquals("Dispo überzogen", warning?.title)
    }

    @Test
    fun cardBeyondItsLimit_keepsTheCardWording() {
        val card = account(type = AccountType.CREDIT_CARD, limit = 500.0)
        val warning = limitWarningFor(card, balance = -600.0)

        assertEquals(LimitKind.CARD, warning?.kind)
        assertEquals("Limit überschritten", warning?.title)
    }

    @Test
    fun everyNonCardTypeCountsAsOverdraft() {
        AccountType.entries.forEach { type ->
            val expected = if (type == AccountType.CREDIT_CARD) LimitKind.CARD else LimitKind.OVERDRAFT
            assertEquals(expected, limitKindFor(type), "Kontotyp $type")
        }
    }

    // ── warning before the limit is gone, not after ────────────────────────────

    @Test
    fun ninetyPercentUsed_warnsAlready() {
        val card = account(type = AccountType.CREDIT_CARD, limit = 500.0)
        val warning = limitWarningFor(card, balance = -450.0)

        assertEquals(LimitSeverity.NEAR, warning?.severity)
        assertEquals("Limit fast erreicht", warning?.title)
    }

    @Test
    fun justBelowNinetyPercent_staysQuiet() {
        val card = account(type = AccountType.CREDIT_CARD, limit = 500.0)
        // 449.99 of 500 is 89.998% — one cent short of the threshold.
        assertNull(limitWarningFor(card, balance = -449.99))
    }

    @Test
    fun exactlyAtTheLimit_isNearAndNotYetOver() {
        // computeLimitUsage flags overLimit only above the limit (used > limit), so
        // spending the last cent of the limit must still read as "nearly", not "over".
        val giro = account(type = AccountType.GIRO, limit = 1000.0)
        val warning = limitWarningFor(giro, balance = -1000.0)

        assertEquals(LimitSeverity.NEAR, warning?.severity)
        assertEquals(1.0, warning?.usage?.percentUsed)
    }

    // ── when nothing should be said ────────────────────────────────────────────

    @Test
    fun accountWithoutALimit_neverWarns() {
        assertNull(limitWarningFor(account(type = AccountType.GIRO), balance = -9999.0))
    }

    @Test
    fun accountInTheBlack_neverWarns() {
        assertNull(limitWarningFor(account(limit = 1000.0), balance = 250.0))
    }

    @Test
    fun aLimitOfZero_warnsOnlyOnceThereIsDebt() {
        val giro = account(type = AccountType.GIRO, limit = 0.0)
        assertNull(limitWarningFor(giro, balance = 0.0))
        assertEquals(LimitSeverity.OVER, limitWarningFor(giro, balance = -0.01)?.severity)
    }

    // ── the list both overviews build their rows from ──────────────────────────

    @Test
    fun warnings_listExceededAccountsFirst() {
        val nearCard = account(id = "card", name = "MasterCard", type = AccountType.CREDIT_CARD, limit = 500.0)
        val overGiro = account(id = "giro", name = "Girokonto", type = AccountType.GIRO, limit = 3000.0)
        val quietSaver = account(id = "save", name = "Sparbuch", type = AccountType.SPARKONTO, limit = 1000.0)

        val warnings = limitWarnings(
            accounts = listOf(nearCard, overGiro, quietSaver),
            balances = mapOf("card" to -465.0, "giro" to -3500.0, "save" to 4000.0),
        )

        assertEquals(listOf("Girokonto", "MasterCard"), warnings.map { it.accountName })
        assertEquals(LimitSeverity.OVER, warnings[0].severity)
        assertEquals(LimitSeverity.NEAR, warnings[1].severity)
    }

    @Test
    fun warnings_treatAMissingBalanceAsZero() {
        val giro = account(id = "giro", type = AccountType.GIRO, limit = 1000.0)
        assertTrue(limitWarnings(listOf(giro), balances = emptyMap()).isEmpty())
    }
}
