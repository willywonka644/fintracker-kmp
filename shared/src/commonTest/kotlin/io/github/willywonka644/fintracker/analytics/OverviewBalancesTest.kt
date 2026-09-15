package io.github.willywonka644.fintracker.analytics

import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.AccountType
import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.BookingStatus
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Übersicht's two figures, and why they are allowed to differ (#101).
 *
 * A card's row shows its current cycle; the total shows every card in full. On the real
 * database that gap was 1,324.28 € when the issue was opened and 33.20 € once every
 * settlement had been booked — it is the sum of the closed but unsettled statements, and
 * that is the behaviour pinned here.
 */
class OverviewBalancesTest {

    private val tz = TimeZone.UTC

    /** 11.09.2026, the day this was measured on the real database. */
    private val now = LocalDate(2026, 9, 11).atStartOfDayIn(tz).toEpochMilliseconds()

    private fun at(year: Int, month: Int, day: Int): Long =
        LocalDate(year, month, day).atStartOfDayIn(tz).toEpochMilliseconds()

    private fun booking(
        id: String,
        accountId: String,
        amount: Double,
        on: Long,
        status: BookingStatus = BookingStatus.POSTED,
    ) = Booking(
        id = id,
        accountId = accountId,
        amount = amount,
        description = "test",
        timestamp = on,
        status = status,
        effectiveDate = on,
    )

    private val giro = Account(id = "giro", name = "Girokonto", type = AccountType.GIRO)
    private val karte = Account(
        id = "karte",
        name = "MasterCard",
        type = AccountType.CREDIT_CARD,
        billingStartDay = 20,
    )

    private fun balances(bookings: List<Booking>, accounts: List<Account> = listOf(giro, karte)) =
        overviewBalances(accounts, bookings, nowMillis = now, timeZone = tz)

    @Test
    fun anOrdinaryAccountCountsInFullInBothFigures() {
        val result = balances(
            listOf(
                booking("old", "giro", 1000.0, at(2026, 1, 5)),
                booking("recent", "giro", -200.0, at(2026, 9, 5)),
            ),
            accounts = listOf(giro),
        )

        assertEquals(800.0, result.perAccount["giro"])
        assertEquals(800.0, result.total)
        assertEquals(0.0, result.rowsVersusTotal)
    }

    @Test
    fun aCardsRowShowsOnlyTheCurrentCycle() {
        // On 11.09. with billingStartDay 20, the open cycle runs 20.08.–19.09.
        val result = balances(
            listOf(
                booking("older", "karte", -500.0, at(2026, 7, 3)),
                booking("current", "karte", -80.0, at(2026, 9, 2)),
            ),
            accounts = listOf(karte),
        )

        assertEquals(-80.0, result.perAccount["karte"])
    }

    @Test
    fun theTotalTakesTheCardInFull() {
        // The whole decision: debt older than the 20th has not gone anywhere, and a figure
        // headed "Gesamtvermögen" that leaves it out flatters the picture by that much.
        val result = balances(
            listOf(
                booking("older", "karte", -500.0, at(2026, 7, 3)),
                booking("current", "karte", -80.0, at(2026, 9, 2)),
            ),
            accounts = listOf(karte),
        )

        assertEquals(-580.0, result.total)
        assertEquals(-500.0, result.rowsVersusTotal)
    }

    @Test
    fun aSettledCardMakesTheTwoFiguresAgreeByItself() {
        // The measured finding behind #101: the gap is the closed but unsettled cycles, so
        // booking the settlement closes it without anyone changing a rule.
        val unsettled = balances(
            listOf(
                booking("older", "karte", -500.0, at(2026, 7, 3)),
                booking("current", "karte", -80.0, at(2026, 9, 2)),
            ),
            accounts = listOf(karte),
        )
        val settled = balances(
            listOf(
                booking("older", "karte", -500.0, at(2026, 7, 3)),
                booking("settlement", "karte", 500.0, at(2026, 7, 19)),
                booking("current", "karte", -80.0, at(2026, 9, 2)),
            ),
            accounts = listOf(karte),
        )

        assertEquals(-500.0, unsettled.rowsVersusTotal)
        assertEquals(0.0, settled.rowsVersusTotal)
        assertEquals(settled.perAccount["karte"], settled.total)
    }

    @Test
    fun theGapIsTheClosedCyclesAndNothingElse() {
        val result = balances(
            listOf(
                booking("giro-old", "giro", 2000.0, at(2026, 1, 5)),
                booking("card-closed", "karte", -300.0, at(2026, 5, 4)),
                booking("card-open", "karte", -40.0, at(2026, 9, 1)),
            )
        )

        assertEquals(2000.0, result.perAccount["giro"])
        assertEquals(-40.0, result.perAccount["karte"])
        assertEquals(1660.0, result.total)
        assertEquals(-300.0, result.rowsVersusTotal)
    }

    @Test
    fun plannedBookingsCountInNeitherFigure() {
        // Money that has not moved yet is not part of what one owns.
        val result = balances(
            listOf(
                booking("real", "giro", 1000.0, at(2026, 9, 1)),
                booking("planned", "giro", -400.0, at(2026, 12, 1), status = BookingStatus.SCHEDULED),
            ),
            accounts = listOf(giro),
        )

        assertEquals(1000.0, result.perAccount["giro"])
        assertEquals(1000.0, result.total)
    }

    @Test
    fun anAccountWithoutBookingsIsZeroRatherThanMissing() {
        val result = balances(emptyList())

        assertEquals(0.0, result.perAccount["giro"])
        assertEquals(0.0, result.perAccount["karte"])
        assertEquals(0.0, result.total)
        assertTrue(result.perAccount.keys.containsAll(setOf("giro", "karte")))
    }

    @Test
    fun aCardWithoutABillingStartDayStillGetsACycle() {
        // The screens fall back to 18; the function has to agree rather than crash.
        val noDay = karte.copy(id = "k2", billingStartDay = null)
        val result = balances(
            listOf(booking("x", "k2", -25.0, at(2026, 9, 5))),
            accounts = listOf(noDay),
        )

        assertEquals(-25.0, result.perAccount["k2"])
        assertEquals(-25.0, result.total)
    }

    @Test
    fun theBookingsOwnDateDecidesWhichCycleItFallsIn() {
        // effectiveDate wins over timestamp, as everywhere else in the app.
        val backdated = booking("b", "karte", -60.0, at(2026, 9, 2))
            .copy(timestamp = at(2026, 7, 1))
        val result = balances(listOf(backdated), accounts = listOf(karte))

        assertEquals(-60.0, result.perAccount["karte"])
    }

    @Test
    fun instantsOutsideTheCycleAreExcludedAtTheBoundary() {
        // 19.09. is still the open cycle; 19.08. belongs to the one before it.
        val inside = balances(
            listOf(booking("i", "karte", -10.0, at(2026, 8, 20))),
            accounts = listOf(karte),
        )
        val outside = balances(
            listOf(booking("o", "karte", -10.0, at(2026, 8, 19))),
            accounts = listOf(karte),
        )

        assertEquals(-10.0, inside.perAccount["karte"])
        assertEquals(0.0, outside.perAccount["karte"])
        assertEquals(-10.0, outside.total)
    }

    @Test
    fun theFunctionDoesNotCareWhatOrderTheAccountsArriveIn() {
        val bookings = listOf(
            booking("a", "giro", 500.0, at(2026, 9, 1)),
            booking("b", "karte", -100.0, at(2026, 9, 1)),
        )

        assertEquals(
            balances(bookings, listOf(giro, karte)).total,
            balances(bookings, listOf(karte, giro)).total,
        )
    }
}
