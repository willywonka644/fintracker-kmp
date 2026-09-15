package io.github.willywonka644.fintracker.booking

import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.BookingSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Vorschläge für die Beschreibung einer neuen Buchung (#136).
 */
class DescriptionSuggestionsTest {

    private var seq = 0

    private fun booking(
        description: String,
        category: String? = null,
        day: Int = seq,
        source: BookingSource = BookingSource.MANUAL,
        installmentGroupId: String? = null,
        deleted: Boolean = false,
    ) = Booking(
        id = "b${seq++}",
        accountId = "acc-1",
        amount = -10.0,
        description = description,
        timestamp = 1_700_000_000_000 + day * 86_400_000L,
        category = category,
        source = source,
        installmentGroupId = installmentGroupId,
        deleted = deleted,
    )

    private fun suggest(bookings: List<Booking>, query: String, limit: Int = 5) =
        DescriptionSuggestions.forQuery(DescriptionSuggestions.index(bookings), query, limit)

    @Test
    fun `the most-used description comes first`() {
        val bookings = listOf(
            booking("Spotify"), booking("Spotify"), booking("Spotify"),
            booking("Sparrate"), booking("Sparrate"),
            booking("Spar-Rate"),
        )
        assertEquals(
            listOf("Spotify", "Sparrate", "Spar-Rate"),
            suggest(bookings, "sp").map { it.description },
        )
    }

    @Test
    fun `a match in the middle counts like one at the start`() {
        val bookings = listOf(booking("ETF Sparrate"), booking("ETF Sparrate"), booking("Sparrate"))
        // Nach Haeufigkeit, nicht nach Trefferstelle: "ETF Sparrate" steht oben, weil es
        // haeufiger ist, obwohl "Sparrate" mit dem Getippten anfaengt.
        assertEquals(listOf("ETF Sparrate", "Sparrate"), suggest(bookings, "sparrat").map { it.description })
    }

    @Test
    fun `matching ignores case and umlauts`() {
        val bookings = listOf(booking("Lüfter"), booking("NÜSSE"))
        assertEquals(listOf("Lüfter"), suggest(bookings, "luf").map { it.description })
        assertEquals(listOf("NÜSSE"), suggest(bookings, "nuss").map { it.description })
    }

    @Test
    fun `an empty query offers the most-used overall`() {
        val bookings = listOf(
            booking("Spotify"), booking("Spotify"), booking("Miete"), booking("Strom"),
        )
        assertEquals("Spotify", suggest(bookings, "").first().description)
        assertEquals(3, suggest(bookings, "").size)
    }

    @Test
    fun `what is already typed in full is not offered back`() {
        val bookings = listOf(booking("Spotify"), booking("Spotify Family"))
        assertEquals(listOf("Spotify Family"), suggest(bookings, "Spotify").map { it.description })
        assertTrue(suggest(listOf(booking("Spotify")), "spotify").isEmpty())
    }

    @Test
    fun `the limit is honoured`() {
        val bookings = (1..10).map { booking("Test $it") }
        assertEquals(3, suggest(bookings, "test", limit = 3).size)
    }

    @Test
    fun `the last spelling used is the one offered`() {
        val bookings = listOf(
            booking("spotify", day = 1),
            booking("Spotify", day = 2),
        )
        // Beide sind derselbe Eintrag (Faltung), und angeboten wird die juengere Schreibweise.
        val only = suggest(bookings, "spot").single()
        assertEquals("Spotify", only.description)
        assertEquals(2, only.uses)
    }

    // ── Die Kategorie dazu ───────────────────────────────────────────────────

    @Test
    fun `the category most often used with that text comes along`() {
        val bookings = listOf(
            booking("Spotify", category = "Abos"),
            booking("Spotify", category = "Abos"),
            booking("Spotify", category = "Unterhaltung"),
        )
        assertEquals("Abos", suggest(bookings, "spot").single().category)
    }

    @Test
    fun `on a tie the more recent category wins`() {
        val bookings = listOf(
            booking("Spotify", category = "Unterhaltung", day = 1),
            booking("Spotify", category = "Abos", day = 2),
        )
        assertEquals("Abos", suggest(bookings, "spot").single().category)
    }

    @Test
    fun `bookings without a category do not outvote the ones that have one`() {
        val bookings = listOf(
            booking("Spotify", category = null),
            booking("Spotify", category = null),
            booking("Spotify", category = "Abos"),
        )
        assertEquals("Abos", suggest(bookings, "spot").single().category)
    }

    @Test
    fun `a text that never had a category suggests none`() {
        assertNull(suggest(listOf(booking("Spotify", category = null)), "spot").single().category)
    }

    // ── Was nicht in den Vorrat gehört ───────────────────────────────────────

    @Test
    fun `instalment rates stay out`() {
        val bookings = listOf(
            booking("Rudermaschine 1/12", installmentGroupId = "g1"),
            booking("Rudermaschine 2/12", installmentGroupId = "g1"),
            booking("Rudermaschine 3/12", installmentGroupId = "g1"),
            booking("Rudern im Verein"),
        )
        // Zwoelf Raten wuerden die Liste verstopfen mit etwas, das von Hand nie so
        // eingegeben wird — die Raten erzeugt das Raten-Werkzeug.
        assertEquals(listOf("Rudern im Verein"), suggest(bookings, "rud").map { it.description })
    }

    @Test
    fun `reconciliation rows stay out`() {
        val bookings = listOf(
            booking("Kontoabgleich-Korrektur", source = BookingSource.RECONCILIATION),
            booking("Konto Gebuehren"),
        )
        assertEquals(listOf("Konto Gebuehren"), suggest(bookings, "konto").map { it.description })
    }

    @Test
    fun `deleted and empty rows stay out`() {
        val bookings = listOf(
            booking("Spotify", deleted = true),
            booking(""),
            booking("   "),
            booking("Strom"),
        )
        assertEquals(listOf("Strom"), DescriptionSuggestions.index(bookings).map { it.description })
    }

    @Test
    fun `recurring bookings count like any other`() {
        // Der Text stammt aus einer Dauerauftrag-Regel, aber geschrieben hat ihn jemand,
        // und dass er oft vorkommt, ist der Befund und keine Verzerrung.
        val bookings = (1..5).map { booking("Spotify", category = "Abos", day = it) }
        assertEquals(5, suggest(bookings, "spot").single().uses)
    }

    @Test
    fun `an empty stock offers nothing`() {
        assertTrue(DescriptionSuggestions.index(emptyList()).isEmpty())
        assertTrue(suggest(emptyList(), "irgendwas").isEmpty())
    }

    @Test
    fun `a query nothing matches offers nothing`() {
        assertTrue(suggest(listOf(booking("Miete"), booking("Strom")), "zzz").isEmpty())
    }
}
