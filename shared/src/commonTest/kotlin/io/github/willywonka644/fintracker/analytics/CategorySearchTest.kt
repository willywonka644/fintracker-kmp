package io.github.willywonka644.fintracker.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The category breakdown can be searched (#134).
 *
 * The desktop drew the top eight of 33 categories; the rest were unreachable. The
 * search has to find them without touching any number the card shows.
 */
class CategorySearchTest {

    private val entries = listOf(
        CategoryBreakdownEntry("Lebensmittel", 800.0, 0.40),
        CategoryBreakdownEntry("Lüfter", 400.0, 0.20),
        CategoryBreakdownEntry("Versicherung", 400.0, 0.20),
        CategoryBreakdownEntry("Straße", 200.0, 0.10),
        CategoryBreakdownEntry("Ohne Kategorie", 200.0, 0.10),
    )

    @Test
    fun `blank query keeps every row`() {
        assertEquals(entries, CategorySearch.filter(entries, ""))
        assertEquals(entries, CategorySearch.filter(entries, "   "))
    }

    @Test
    fun `matches anywhere in the name, not only at the start`() {
        assertEquals(listOf("Versicherung"), CategorySearch.filter(entries, "sicher").map { it.categoryName })
        assertEquals(listOf("Lebensmittel"), CategorySearch.filter(entries, "mittel").map { it.categoryName })
    }

    @Test
    fun `ignores case`() {
        assertEquals(listOf("Lebensmittel"), CategorySearch.filter(entries, "LEBENS").map { it.categoryName })
    }

    @Test
    fun `finds an umlaut typed as its base letter`() {
        assertEquals(listOf("Lüfter"), CategorySearch.filter(entries, "luf").map { it.categoryName })
        assertEquals(listOf("Straße"), CategorySearch.filter(entries, "strasse").map { it.categoryName })
    }

    @Test
    fun `folding goes one way - ue does not find u-umlaut`() {
        assertFalse(CategorySearch.matches("Lüfter", "luefter"))
    }

    @Test
    fun `keeps the ranking by amount`() {
        val hits = CategorySearch.filter(entries, "e")
        assertEquals(hits.sortedByDescending { it.amount }, hits)
    }

    @Test
    fun `a query nothing matches yields nothing`() {
        assertTrue(CategorySearch.filter(entries, "zzz").isEmpty())
    }

    @Test
    fun `the uncategorized row is searchable like any other`() {
        assertEquals(listOf("Ohne Kategorie"), CategorySearch.filter(entries, "ohne").map { it.categoryName })
    }

    @Test
    fun `no percentage is recalculated`() {
        val hit = CategorySearch.filter(entries, "sicher").single()
        assertEquals(0.20, hit.percentage)
        assertEquals(400.0, hit.amount)
    }
}
