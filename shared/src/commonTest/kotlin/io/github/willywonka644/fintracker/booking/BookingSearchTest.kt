package io.github.willywonka644.fintracker.booking

import io.github.willywonka644.fintracker.Booking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Suche nach Bezeichnung oder Kategorie (#137).
 */
class BookingSearchTest {

    private var seq = 0

    private fun booking(
        description: String,
        category: String? = null,
        rawDescription: String? = null,
        merchantName: String? = null,
    ) = Booking(
        id = "b${seq++}",
        accountId = "acc-1",
        amount = -10.0,
        description = description,
        timestamp = 1_700_000_000_000,
        category = category,
        rawDescription = rawDescription,
        merchantName = merchantName,
    )

    private val stock = listOf(
        booking("ETF Sparrate", category = "Finanzen/Investment"),
        booking("Lüfter 4", category = "IT-Equipment"),
        booking("Wocheneinkauf", category = "Lebensmittel"),
        booking("Kaffee", category = null),
    )

    @Test
    fun `a blank query filters nothing`() {
        assertEquals(stock, BookingSearch.filter(stock, ""))
        assertEquals(stock, BookingSearch.filter(stock, "   "))
    }

    @Test
    fun `finds by description`() {
        assertEquals(listOf("ETF Sparrate"), BookingSearch.filter(stock, "sparrate").map { it.description })
    }

    @Test
    fun `finds by category`() {
        assertEquals(listOf("Wocheneinkauf"), BookingSearch.filter(stock, "lebensmittel").map { it.description })
    }

    @Test
    fun `one query, both fields — the hit can come from either side`() {
        val mixed = listOf(
            booking("Spotify", category = "Abos"),
            booking("Abopauschale", category = "Sonstiges"),
        )
        assertEquals(2, BookingSearch.filter(mixed, "abo").size)
    }

    @Test
    fun `ignores case and umlauts`() {
        assertEquals(listOf("Lüfter 4"), BookingSearch.filter(stock, "LUF").map { it.description })
        assertTrue(BookingSearch.matches(booking("Straße"), "strasse"))
    }

    @Test
    fun `a booking without a category is searchable by its description`() {
        assertEquals(listOf("Kaffee"), BookingSearch.filter(stock, "kaffee").map { it.description })
    }

    @Test
    fun `import-only fields are not searched`() {
        // Sie stehen nirgends auf dem Schirm; ein Treffer darin saehe aus wie ein Fehler.
        val imported = booking("Lastschrift", rawDescription = "REWE SAGT DANKE", merchantName = "REWE")
        assertFalse(BookingSearch.matches(imported, "rewe"))
        assertTrue(BookingSearch.matches(imported, "lastschrift"))
    }

    @Test
    fun `a query nothing matches yields nothing`() {
        assertTrue(BookingSearch.filter(stock, "zzz").isEmpty())
    }

    @Test
    fun `the order the bookings came in survives`() {
        val hits = BookingSearch.filter(stock, "e")
        assertEquals(hits, stock.filter { it in hits })
    }

    // ── Für die Auswertungen ─────────────────────────────────────────────────

    @Test
    fun `matchingCategories names the rows a query touches`() {
        assertEquals(setOf("Finanzen/Investment"), BookingSearch.matchingCategories(stock, "sparrate"))
    }

    @Test
    fun `a hit on a booking without a category lands under Ohne Kategorie`() {
        assertEquals(setOf("Ohne Kategorie"), BookingSearch.matchingCategories(stock, "kaffee"))
    }

    @Test
    fun `one query can touch several categories`() {
        val mixed = listOf(
            booking("Kaffee to go", category = "Essen gehen"),
            booking("Kaffeebohnen", category = "Lebensmittel"),
        )
        assertEquals(setOf("Essen gehen", "Lebensmittel"), BookingSearch.matchingCategories(mixed, "kaffee"))
    }

    @Test
    fun `a blank query touches every category that is there`() {
        assertEquals(
            setOf("Finanzen/Investment", "IT-Equipment", "Lebensmittel", "Ohne Kategorie"),
            BookingSearch.matchingCategories(stock, ""),
        )
    }
}
