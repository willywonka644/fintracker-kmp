package io.github.willywonka644.fintracker.booking

import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.BookingSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The rule that proposes which existing bookings are really one transfer (#122).
 *
 * The tolerance in here is not a taste: it was measured against the real database on
 * 10.09.2026. The two cases that fix it from either side — a card settlement eleven days
 * apart that must be found, and two unrelated 22.00 € sixteen days apart that must not —
 * are both below as tests.
 */
class TransferSuggestionsTest {

    private val day = 86_400_000L
    private val base = 1_600_000_000_000L

    private fun booking(
        id: String,
        accountId: String,
        amount: Double,
        dayOffset: Long = 0,
        source: BookingSource = BookingSource.MANUAL,
        category: String? = null,
        deleted: Boolean = false,
        description: String = "test",
    ) = Booking(
        id = id,
        accountId = accountId,
        amount = amount,
        description = description,
        timestamp = base + dayOffset * day,
        category = category,
        source = source,
        deleted = deleted,
    )

    @Test
    fun aMirroredPairOnTwoAccountsIsProposed() {
        val out = booking("out", "giro", -450.0)
        val into = booking("in", "dkb", 450.0)

        val found = TransferSuggestions.find(listOf(out, into, booking("x", "giro", -20.0)))

        assertEquals(1, found.size)
        assertEquals("out", found.single().outgoing.id)
        assertEquals("in", found.single().incoming.id)
        assertEquals(450.0, found.single().amount)
        assertEquals(0, found.single().daysApart)
    }

    @Test
    fun theSameAccountOnBothSidesIsNotATransfer() {
        val found = TransferSuggestions.find(
            listOf(booking("out", "giro", -450.0), booking("in", "giro", 450.0))
        )

        assertTrue(found.isEmpty())
    }

    @Test
    fun aSettlementElevenDaysApartIsStillFound() {
        // The case that forbids a tighter window: measured, card settlements land on the
        // card and on the current account six to eleven days apart.
        val out = booking("giro-side", "giro", -2314.13, dayOffset = 11)
        val into = booking("card-side", "karte", 2314.13, dayOffset = 0)

        val found = TransferSuggestions.find(listOf(out, into))

        assertEquals(1, found.size)
        assertEquals(11, found.single().daysApart)
    }

    @Test
    fun twoUnrelatedBookingsSixteenDaysApartAreNotProposed() {
        // The measured first false match: a haircut on the card and a window repair on the
        // current account, both 22.00 €, sixteen days apart. Fourteen days keeps them apart.
        val haircut = booking("haircut", "karte", -22.0, dayOffset = 16, description = "Friseur")
        val window = booking("window", "giro", 22.0, dayOffset = 0, description = "Fensterscheibe")

        assertTrue(TransferSuggestions.find(listOf(haircut, window)).isEmpty())
        assertEquals(1, TransferSuggestions.find(listOf(haircut, window), toleranceDays = 16).size)
    }

    @Test
    fun theBoundaryItselfCounts() {
        val out = booking("out", "giro", -100.0, dayOffset = 14)
        val into = booking("in", "dkb", 100.0, dayOffset = 0)

        assertEquals(1, TransferSuggestions.find(listOf(out, into)).size)
        assertTrue(TransferSuggestions.find(listOf(out, into), toleranceDays = 13).isEmpty())
    }

    @Test
    fun aBookingIsProposedInAtMostOnePair() {
        // Two possible partners for the same outgoing side. The closer one wins, and the
        // other must not turn up in a second suggestion — confirming both would give one
        // booking two partners.
        val out = booking("out", "giro", -50.0, dayOffset = 0)
        val near = booking("near", "dkb", 50.0, dayOffset = 1)
        val far = booking("far", "tagesgeld", 50.0, dayOffset = 9)

        val found = TransferSuggestions.find(listOf(out, near, far))

        assertEquals(1, found.size)
        assertEquals("near", found.single().incoming.id)
    }

    @Test
    fun bookingsAlreadyMarkedAsTransfersAreLeftAlone() {
        val out = booking("out", "giro", -450.0, source = BookingSource.TRANSFER)
        val into = booking("in", "dkb", 450.0, source = BookingSource.TRANSFER)

        assertTrue(TransferSuggestions.find(listOf(out, into)).isEmpty())
    }

    @Test
    fun aCorrectionCanStillBeATransferAndIsFlaggedAsOne() {
        // Measured on the real database: four settlement counter-entries are typed as
        // corrections so they would not count as income. Excluding corrections outright
        // would leave exactly those four unreachable.
        val out = booking("out", "giro", -1063.57)
        val into = booking("in", "karte", 1063.57, source = BookingSource.RECONCILIATION)

        val found = TransferSuggestions.find(listOf(out, into))

        assertEquals(1, found.size)
        assertTrue(found.single().wasCorrection, "a correction on either side has to be flagged")
    }

    @Test
    fun anOrdinaryPairIsNotFlaggedAsACorrection() {
        val found = TransferSuggestions.find(
            listOf(booking("out", "giro", -450.0), booking("in", "dkb", 450.0))
        )

        assertTrue(!found.single().wasCorrection)
    }

    @Test
    fun aLoneCorrectionWithoutAMirroredPartnerNeverSurfaces() {
        // The two genuine Kontoabgleich corrections in the real database have no partner,
        // which is why letting corrections through costs nothing.
        val correction = booking("corr", "giro", -1836.15, source = BookingSource.RECONCILIATION)
        val unrelated = booking("other", "dkb", 16.13, source = BookingSource.RECONCILIATION)

        assertTrue(TransferSuggestions.find(listOf(correction, unrelated)).isEmpty())
    }

    @Test
    fun deletedRowsAreNotProposed() {
        val out = booking("out", "giro", -450.0, deleted = true)
        val into = booking("in", "dkb", 450.0)

        assertTrue(TransferSuggestions.find(listOf(out, into)).isEmpty())
    }

    @Test
    fun aRejectedPairStaysRejected() {
        // Without this the same pair comes back every time the tool is opened, and a
        // decision the user already made would have to be made again.
        val out = booking("out", "giro", -450.0)
        val into = booking("in", "dkb", 450.0)
        val key = TransferSuggestions.pairKey("out", "in")

        assertTrue(TransferSuggestions.find(listOf(out, into), rejected = setOf(key)).isEmpty())
    }

    @Test
    fun aPairHasOneIdentityWhicheverSideIsNamedFirst() {
        assertEquals(
            TransferSuggestions.pairKey("a", "b"),
            TransferSuggestions.pairKey("b", "a"),
        )
    }

    @Test
    fun rejectionsSurviveBeingWrittenOutAndReadBack() {
        val first = TransferSuggestions.withRejection(null, TransferSuggestions.pairKey("a", "b"))
        val both = TransferSuggestions.withRejection(first, TransferSuggestions.pairKey("c", "d"))

        assertEquals(
            setOf("a|b", "c|d"),
            TransferSuggestions.parseRejected(both),
        )
        // Rejecting the same pair twice must not make it two entries.
        assertEquals(2, TransferSuggestions.parseRejected(
            TransferSuggestions.withRejection(both, TransferSuggestions.pairKey("b", "a"))
        ).size)
        assertTrue(TransferSuggestions.parseRejected(null).isEmpty())
        assertTrue(TransferSuggestions.parseRejected("").isEmpty())
    }

    @Test
    fun confirmingMarksBothSidesIntoOneGroup() {
        val candidate = TransferSuggestions.find(
            listOf(booking("out", "giro", -450.0), booking("in", "dkb", 450.0))
        ).single()

        val (out, into) = candidate.confirm(groupId = "grp-1", now = 999L)

        assertEquals(BookingSource.TRANSFER, out.source)
        assertEquals(BookingSource.TRANSFER, into.source)
        assertEquals("grp-1", out.transferGroupId)
        assertEquals("grp-1", into.transferGroupId)
        assertEquals(999L, out.lastModifiedAt)
        assertEquals(999L, into.lastModifiedAt)
    }

    @Test
    fun confirmingKeepsWhateverCategoryWasThere() {
        // Overwriting it would throw away the only description some of these rows have,
        // and the analytics go by the type anyway.
        val candidate = TransferSuggestions.find(
            listOf(
                booking("out", "giro", -450.0, category = "Finanzen/Investment"),
                booking("in", "dkb", 450.0, category = "Finanzen/Investment"),
            )
        ).single()

        val (out, into) = candidate.confirm(groupId = "grp-1", now = 999L)

        assertEquals("Finanzen/Investment", out.category)
        assertEquals("Finanzen/Investment", into.category)
    }

    @Test
    fun aConfirmedPairIsNotProposedAgain() {
        val bookings = listOf(booking("out", "giro", -450.0), booking("in", "dkb", 450.0))
        val candidate = TransferSuggestions.find(bookings).single()
        val (out, into) = candidate.confirm(groupId = "grp-1", now = 999L)

        assertTrue(TransferSuggestions.find(listOf(out, into)).isEmpty())
    }

    @Test
    fun theEffectiveDateWinsOverTheTimestamp() {
        // Everything else in the app filters by effectiveDate where it is set; a pair that
        // is close on its effective dates but far apart on its raw timestamps is still one.
        val out = booking("out", "giro", -75.0, dayOffset = 40).copy(effectiveDate = base)
        val into = booking("in", "dkb", 75.0, dayOffset = 0)

        val found = TransferSuggestions.find(listOf(out, into))

        assertNotNull(found.singleOrNull())
        assertEquals(0, found.single().daysApart)
    }
}
