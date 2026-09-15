package io.github.willywonka644.fintracker.analytics

import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.BookingSource
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Die zwei Fragen an das Suchfeld über der Kategorie-Aufschlüsselung (#137).
 */
class BreakdownSearchTest {

    private var seq = 0

    private fun booking(
        amount: Double,
        description: String,
        category: String? = null,
        source: BookingSource = BookingSource.MANUAL,
    ) = Booking(
        id = "b${seq++}",
        accountId = "acc-1",
        amount = amount,
        description = description,
        timestamp = 1_700_000_000_000,
        category = category,
        source = source,
    )

    private val stock = listOf(
        booking(-600.0, "ETF Sparrate", "Investment"),
        booking(-400.0, "ETF Sparrate", "Investment"),
        booking(-500.0, "Wocheneinkauf", "Lebensmittel"),
        booking(-300.0, "ETF Gebühren", "Bankgebühren"),
        booking(-200.0, "Kino", "Freizeit"),
        booking(2000.0, "Gehalt", "Gehalt"),
    )

    private val breakdown = calculateCategoryBreakdown(stock)

    private fun apply(query: String, mode: BreakdownSearch.Mode) =
        BreakdownSearch.apply(stock, breakdown, query, mode)

    // ── Leerer Suchtext ──────────────────────────────────────────────────────

    @Test
    fun `a blank query changes nothing, in either mode`() {
        for (mode in BreakdownSearch.Mode.entries) {
            val r = apply("", mode)
            assertEquals(breakdown, r.entries)
            assertFalse(r.recomputed)
        }
    }

    // ── Modus Kategorie: filtert nur ─────────────────────────────────────────

    @Test
    fun `category mode filters rows and touches no number`() {
        val r = apply("invest", BreakdownSearch.Mode.CATEGORY)
        assertFalse(r.recomputed)
        val row = r.entries.single()
        assertEquals("Investment", row.categoryName)
        // 1000 von 2000 Ausgaben — der Anteil am ganzen Zeitraum, nicht an sich selbst.
        assertEquals(1000.0, row.amount)
        assertEquals(0.5, row.percentage)
    }

    @Test
    fun `category mode does not find a description`() {
        assertTrue(apply("wocheneinkauf", BreakdownSearch.Mode.CATEGORY).entries.isEmpty())
    }

    // ── Modus Bezeichnung: rechnet neu ───────────────────────────────────────

    @Test
    fun `description mode recomputes against the hits`() {
        val r = apply("ETF", BreakdownSearch.Mode.DESCRIPTION)
        assertTrue(r.recomputed)
        assertEquals(3, r.hits)
        assertEquals(1300.0, r.hitTotal)

        val byName = r.entries.associateBy { it.categoryName }
        assertEquals(setOf("Investment", "Bankgebühren"), byName.keys)
        assertEquals(1000.0, byName.getValue("Investment").amount)
        assertEquals(300.0, byName.getValue("Bankgebühren").amount)
    }

    @Test
    fun `the percentages of a recomputed breakdown add up to one`() {
        val r = apply("ETF", BreakdownSearch.Mode.DESCRIPTION)
        assertEquals(1.0, r.entries.sumOf { it.percentage }, absoluteTolerance = 1e-9)
    }

    @Test
    fun `the rows add up to the total shown above them`() {
        // Der Punkt aus #125: was auf dem Schirm steht, muss sich nachrechnen lassen.
        val r = apply("ETF", BreakdownSearch.Mode.DESCRIPTION)
        assertEquals(r.hitTotal, r.entries.sumOf { it.amount }, absoluteTolerance = 1e-9)
    }

    @Test
    fun `description mode also finds by category name`() {
        // Ein Feld, beide Felder — die Frage im Kopf ist nicht getrennt.
        val r = apply("Lebensmittel", BreakdownSearch.Mode.DESCRIPTION)
        assertEquals(1, r.hits)
        assertEquals("Lebensmittel", r.entries.single().categoryName)
    }

    @Test
    fun `the drilldown sees exactly the bookings the bars are made of`() {
        val r = apply("ETF", BreakdownSearch.Mode.DESCRIPTION)
        assertEquals(r.hits, r.bookings.size)
        assertEquals(r.hitTotal, r.bookings.sumOf { abs(it.amount) }, absoluteTolerance = 1e-9)
    }

    @Test
    fun `a query nothing matches yields an empty card, not a wrong one`() {
        val r = apply("zzz", BreakdownSearch.Mode.DESCRIPTION)
        assertTrue(r.entries.isEmpty())
        assertEquals(0, r.hits)
        assertEquals(0.0, r.hitTotal)
    }

    // ── Seite und Umbuchungen ────────────────────────────────────────────────

    @Test
    fun `income is searched on the income side only`() {
        val r = BreakdownSearch.apply(
            stock, breakdown, "Gehalt", BreakdownSearch.Mode.DESCRIPTION,
            side = CategoryBreakdownSide.INCOME,
        )
        assertEquals(1, r.hits)
        assertEquals(2000.0, r.hitTotal)
        assertEquals("Gehalt", r.entries.single().categoryName)
    }

    @Test
    fun `an expense is not found while the income side is shown`() {
        val r = BreakdownSearch.apply(
            stock, breakdown, "Kino", BreakdownSearch.Mode.DESCRIPTION,
            side = CategoryBreakdownSide.INCOME,
        )
        assertEquals(0, r.hits)
    }

    @Test
    fun `transfers stay out of a recomputed breakdown too`() {
        val withTransfer = stock + booking(-900.0, "ETF Umbuchung", "Investment", BookingSource.TRANSFER)
        val r = BreakdownSearch.apply(
            withTransfer, calculateCategoryBreakdown(withTransfer), "ETF",
            BreakdownSearch.Mode.DESCRIPTION,
        )
        // Eine Umbuchung ist kein Aufwand; sie darf auch in der Suche keiner werden.
        assertEquals(3, r.hits)
        assertEquals(1300.0, r.hitTotal)
    }

    @Test
    fun `without the transfer filter a transfer counts, as everywhere else`() {
        val withTransfer = stock + booking(-900.0, "ETF Umbuchung", "Investment", BookingSource.TRANSFER)
        val r = BreakdownSearch.apply(
            withTransfer, calculateCategoryBreakdown(withTransfer, filterTransfers = false), "ETF",
            BreakdownSearch.Mode.DESCRIPTION, filterTransfers = false,
        )
        assertEquals(4, r.hits)
        assertEquals(2200.0, r.hitTotal)
    }

    @Test
    fun `umlauts fold in this field too`() {
        val r = apply("gebuhren", BreakdownSearch.Mode.DESCRIPTION)
        assertEquals(1, r.hits)
        assertEquals("Bankgebühren", r.entries.single().categoryName)
    }
}
