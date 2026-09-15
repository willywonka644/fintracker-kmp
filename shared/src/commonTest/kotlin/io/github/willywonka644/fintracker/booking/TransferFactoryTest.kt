package io.github.willywonka644.fintracker.booking

import io.github.willywonka644.fintracker.BookingSource
import io.github.willywonka644.fintracker.BookingStatus
import io.github.willywonka644.fintracker.analytics.excludeTransfers
import io.github.willywonka644.fintracker.analytics.sumExpenses
import io.github.willywonka644.fintracker.analytics.sumIncome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransferFactoryTest {

    private var nextId = 0
    private fun ids(): String = "b${nextId++}"
    private fun groupIds(): String = "grp-1"

    private val day = 86_400_000L
    private val past = 1_600_000_000_000L

    private fun transfer(
        from: String = "giro",
        to: String = "karte",
        amount: Double = 400.0,
        fromDate: Long = past,
        toDate: Long = past,
    ) = TransferFactory.create(
        idProvider = ::ids,
        groupIdProvider = ::groupIds,
        fromAccountId = from,
        toAccountId = to,
        amount = amount,
        description = "Kartenabrechnung",
        fromDate = fromDate,
        toDate = toDate,
    )

    @Test
    fun theTwoSidesMirrorEachOtherAndShareAGroup() {
        val (out, incoming) = transfer()

        assertEquals(-400.0, out.amount)
        assertEquals(400.0, incoming.amount)
        assertEquals("giro", out.accountId)
        assertEquals("karte", incoming.accountId)
        assertEquals(out.transferGroupId, incoming.transferGroupId)
        assertNotNull(out.transferGroupId)
        assertTrue(out.id != incoming.id)
    }

    @Test
    fun bothSidesAreMarkedAsTransfers() {
        val (out, incoming) = transfer()

        assertEquals(BookingSource.TRANSFER, out.source)
        assertEquals(BookingSource.TRANSFER, incoming.source)
    }

    @Test
    fun neitherSideCarriesACategory() {
        // No category means nothing to rename that could switch the handling off —
        // the exact failure the old settlement rule had.
        val (out, incoming) = transfer()

        assertNull(out.category)
        assertNull(incoming.category)
    }

    @Test
    fun theSidesMayFallOnDifferentDays() {
        // Measured on the real database: a settlement lands on the card and on the
        // current account six to eleven days apart.
        val (out, incoming) = transfer(fromDate = past + 11 * day, toDate = past)

        assertEquals(past + 11 * day, out.effectiveDate)
        assertEquals(past, incoming.effectiveDate)
    }

    @Test
    fun eachSideGetsItsOwnStatusFromItsOwnDate() {
        // One side already posted while the other is still ahead is a real case,
        // so status is decided per side rather than for the pair.
        val farFuture = past + 4_000L * day
        val (out, incoming) = transfer(fromDate = past, toDate = farFuture)

        assertEquals(BookingStatus.POSTED, out.status)
        assertEquals(BookingStatus.SCHEDULED, incoming.status)
    }

    @Test
    fun aTransferMovesNoTotals() {
        // The whole point of #118: both halves drop out together, so income and
        // expense are untouched by money that only changed accounts.
        val (out, incoming) = transfer()
        val bookings = listOf(out, incoming)

        assertTrue(bookings.excludeTransfers().isEmpty())
        assertEquals(0.0, sumIncome(bookings))
        assertEquals(0.0, sumExpenses(bookings))
    }

    @Test
    fun theSameAccountOnBothSidesIsRefused() {
        assertEquals(
            "Eine Umbuchung braucht zwei verschiedene Konten.",
            TransferFactory.validate("giro", "giro", 400.0),
        )
        assertFailsWith<IllegalArgumentException> { transfer(from = "giro", to = "giro") }
    }

    @Test
    fun anAmountOfZeroOrLessIsRefused() {
        assertNotNull(TransferFactory.validate("giro", "karte", 0.0))
        assertNotNull(TransferFactory.validate("giro", "karte", -5.0))
        assertNull(TransferFactory.validate("giro", "karte", 0.01))
    }

    @Test
    fun aMissingAccountIsRefusedBeforeTheAmountIsLookedAt() {
        assertNotNull(TransferFactory.validate(null, "karte", 400.0))
        assertNotNull(TransferFactory.validate("giro", null, 400.0))
        assertNotNull(TransferFactory.validate("giro", "karte", null))
    }

    @Test
    fun theGroupFindsBothSidesAndSkipsDeletedOnes() {
        val (out, incoming) = transfer()
        val unrelated = BookingFactory.createManualBooking(
            idProvider = { "other" },
            accountId = "giro",
            amount = -50.0,
            description = "Lebensmittel",
            category = "Lebensmittel",
        )
        val all = listOf(out, incoming, unrelated)

        val group = all.transferGroup(out.transferGroupId!!)
        assertEquals(listOf(out.id, incoming.id), group.map { it.id })

        val withoutIncoming = listOf(out, incoming.copy(deleted = true), unrelated)
        assertEquals(listOf(out.id), withoutIncoming.transferGroup(out.transferGroupId!!).map { it.id })
    }

    @Test
    fun theCounterpartIsFoundFromEitherSide() {
        val (out, incoming) = transfer()
        val all = listOf(out, incoming)

        assertEquals(incoming.id, all.transferCounterpart(out)?.id)
        assertEquals(out.id, all.transferCounterpart(incoming)?.id)
    }

    @Test
    fun aHalfTransferReportsNoCounterpart() {
        // Not merely a programming error: the stock holds settlements whose
        // counter-entry was never booked (#123), and #122 marks one side first.
        val (out, _) = transfer()

        assertNull(listOf(out).transferCounterpart(out))
    }

    @Test
    fun anOrdinaryBookingHasNoCounterpart() {
        val plain = BookingFactory.createManualBooking(
            idProvider = { "plain" },
            accountId = "giro",
            amount = -50.0,
            description = "Lebensmittel",
            category = "Lebensmittel",
        )

        assertNull(listOf(plain).transferCounterpart(plain))
    }
}
