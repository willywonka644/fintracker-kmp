package io.github.willywonka644.fintracker.analytics

import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.AccountType
import io.github.willywonka644.fintracker.Booking
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone

/**
 * What the Übersicht shows: a figure per account, and the total above them (#101).
 *
 * The two are **deliberately not the same arithmetic**, and that is the point of this type.
 *
 * A credit card's row answers "what is running up for the next statement", so it counts only
 * the current billing cycle — that is the number to hold against the card statement. The
 * total above cannot use that: the rest of the card's debt is not gone, it is merely older
 * than the 20th, and a figure headed "Gesamtvermögen" that leaves it out flatters the
 * picture. So the total takes every account in full.
 *
 * That difference used to be 1,324.28 €, which is what #101 was opened about. Measured again
 * on 11.09.2026, after every card settlement had been booked as a transfer (#122, #123), it
 * was **33.20 €** — the gap is the sum of the *closed but unbooked* statements, so it closes
 * itself as long as settlements get entered. It reopens quietly when one is missed, which is
 * exactly why the total no longer relies on them being there.
 *
 * The row and the total will therefore differ by whatever sits in closed cycles. The card's
 * row says "laufende Abrechnung" so nobody has to work that out from the discrepancy.
 */
data class OverviewBalances(
    /** Per account, as its row shows it — a card over its current cycle only. */
    val perAccount: Map<String, Double>,
    /** Every account in full, cards included. The figure headed "Gesamtvermögen". */
    val total: Double,
) {
    /** How far the total sits from the sum of the rows — the closed, unsettled cycles. */
    val rowsVersusTotal: Double get() = total - perAccount.values.sum()
}

fun overviewBalances(
    accounts: List<Account>,
    bookings: List<Booking>,
    nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): OverviewBalances {
    fun fullBalance(accountId: String) =
        filterBookingsForAccount(bookings, accountId, allTimePeriod()).sumOf { it.amount }

    return OverviewBalances(
        perAccount = accounts.associate { account ->
            account.id to when (account.type) {
                AccountType.CREDIT_CARD ->
                    filterBookingsForAccount(
                        bookings,
                        account.id,
                        currentBillingCyclePeriod(account.billingStartDay ?: 18, nowMillis, timeZone),
                    ).sumOf { it.amount }
                else -> fullBalance(account.id)
            }
        },
        total = accounts.sumOf { fullBalance(it.id) },
    )
}
