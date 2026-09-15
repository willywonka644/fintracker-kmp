package io.github.willywonka644.fintracker.analytics

import io.github.willywonka644.fintracker.TextFold

/**
 * Finding one category in the breakdown list, by typing part of its name.
 *
 * The breakdown is ranked by amount, which is the right order for "where did the money
 * go" and the wrong one for "what did I spend on Versicherung". The real stock carries
 * 33 categories; on the desktop only the first eight were ever drawn, so everything
 * below rank eight had no way of being looked at at all.
 *
 * This only decides **which rows are listed**. It changes no number: [CategoryBreakdownEntry.percentage]
 * stays the share of the whole period, and the donut keeps showing all of it — a share
 * recomputed against three hand-picked rows would answer a question nobody asked.
 * The list is expected to say so while a query is active.
 *
 * It lives in `shared` so both platforms match: the same query has to select the same
 * rows on the phone and on the desktop, or the same screenshot means two things.
 *
 * Matching is case- and umlaut-insensitive; the folding rule is [TextFold].
 */
object CategorySearch {

    /** Whether [name] contains [query] anywhere. A blank query matches everything. */
    fun matches(name: String, query: String): Boolean {
        val needle = TextFold.fold(query.trim())
        if (needle.isEmpty()) return true
        return TextFold.fold(name).contains(needle)
    }

    /**
     * The rows to list for [query], in the order they came in — the ranking by amount
     * survives the search, so a filtered list still reads biggest first.
     */
    fun filter(
        entries: List<CategoryBreakdownEntry>,
        query: String,
    ): List<CategoryBreakdownEntry> =
        if (query.isBlank()) entries else entries.filter { matches(it.categoryName, query) }
}
