package io.github.willywonka644.fintracker.analytics

import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.AccountType

/**
 * Limit and overdraft warnings (#99).
 *
 * The arithmetic was never the problem — [computeLimitUsage] has always been blind to
 * the account type. What was missing is that both overviews only ever asked credit
 * cards, so the account sitting deepest in the red could not carry a limit at all.
 */

/**
 * How full a limit may get before the app says something.
 *
 * Warning only once the limit is exceeded is the warning at the latest possible
 * moment: a card at 93% used to say nothing at all until it tipped over.
 */
const val LIMIT_WARNING_THRESHOLD = 0.9

/** What the limit is called here. Same arithmetic, different word. */
enum class LimitKind { CARD, OVERDRAFT }

/** Order matters: OVER sorts after NEAR, and warnings are listed most urgent first. */
enum class LimitSeverity { NEAR, OVER }

data class LimitWarning(
    val accountId: String,
    val accountName: String,
    val kind: LimitKind,
    val severity: LimitSeverity,
    val usage: LimitUsage,
) {
    /**
     * The wording lives in shared rather than in each UI on purpose. The two platforms
     * have drifted apart on account arithmetic before (#100, #101), and a warning that
     * reads differently on the phone than on the desktop is the same defect in slower
     * motion — with the added cost of a case distinction at every call site.
     */
    val title: String
        get() = when (kind) {
            LimitKind.CARD -> when (severity) {
                LimitSeverity.OVER -> "Limit überschritten"
                LimitSeverity.NEAR -> "Limit fast erreicht"
            }
            LimitKind.OVERDRAFT -> when (severity) {
                LimitSeverity.OVER -> "Dispo überzogen"
                LimitSeverity.NEAR -> "Dispo fast ausgeschöpft"
            }
        }
}

fun limitKindFor(type: AccountType): LimitKind =
    if (type == AccountType.CREDIT_CARD) LimitKind.CARD else LimitKind.OVERDRAFT

/**
 * The warning for one account, or null when it carries no limit or has nothing to say.
 *
 * [balance] is handed in rather than computed: a credit card is measured over its
 * current billing cycle and every other account all-time, and only the caller knows
 * which of the two it is already holding.
 */
fun limitWarningFor(account: Account, balance: Double): LimitWarning? {
    val limit = account.spendingLimit ?: return null
    val usage = computeLimitUsage(balance, limit)
    val severity = when {
        usage.overLimit -> LimitSeverity.OVER
        usage.percentUsed >= LIMIT_WARNING_THRESHOLD -> LimitSeverity.NEAR
        else -> return null
    }
    return LimitWarning(
        accountId = account.id,
        accountName = account.name,
        kind = limitKindFor(account.type),
        severity = severity,
        usage = usage,
    )
}

/**
 * Every account with something to warn about, exceeded limits first. Both overviews
 * build their notification rows from this, so the phone and the desktop cannot end up
 * warning about different accounts.
 */
fun limitWarnings(accounts: List<Account>, balances: Map<String, Double>): List<LimitWarning> =
    accounts
        .mapNotNull { account -> limitWarningFor(account, balances[account.id] ?: 0.0) }
        .sortedByDescending { it.severity }
