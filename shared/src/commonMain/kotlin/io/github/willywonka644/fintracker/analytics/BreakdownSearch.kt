package io.github.willywonka644.fintracker.analytics

import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.booking.BookingSearch
import kotlin.math.abs

/**
 * Das Suchfeld über der Kategorie-Aufschlüsselung — und was es je nach Frage bedeutet (#137).
 *
 * Zwei Fragen passen nicht in ein Verhalten:
 *
 * - **„Wo ist die Zeile Versicherung?"** — eine Suche in der Liste. Die Zahlen bleiben, was
 *   sie sind: Anteile am ganzen Zeitraum. Ein an drei Treffern neu gerechneter Anteil wäre
 *   immer 100 % und damit wertlos.
 * - **„Wie verteilt sich alles, was ETF heißt?"** — eine Einschränkung des Bestands. Hier
 *   *muss* neu gerechnet werden, sonst stünde neben einer Zeile, die wegen drei Treffern da
 *   ist, der Betrag über vierzig Buchungen.
 *
 * Das Zweite ist genau die Zahl, vor der #125 warnt: eine, die sich aus dem Sichtbaren nicht
 * nachrechnen lässt, wirkt wie ein Rechenfehler und schickt die Suche in die Daten statt in
 * den Filter. Deshalb ist der Modus eine **sichtbare Wahl** und kein stiller Schalter, der
 * daran hängt, was der Text zufällig trifft — unterschieden wird durch Beschriftung, wie es
 * #119 für die Filterarten festgehalten hat.
 */
object BreakdownSearch {

    /** Wonach das Feld sucht. */
    enum class Mode {
        /** Kategorienamen. Filtert nur die Liste; keine Zahl ändert sich. */
        CATEGORY,

        /** Buchungstexte. Schränkt den Bestand ein; alle Zahlen beziehen sich dann darauf. */
        DESCRIPTION,
    }

    /**
     * @param entries    Die zu zeichnenden Zeilen — auch die Quelle für Donut und Prozente.
     * @param bookings   Die Buchungen, auf die sich [entries] beziehen; die Quelle für den
     *                   Drilldown, damit eine aufgeklappte Zeile dasselbe meint wie ihr Balken.
     * @param recomputed Ob gegen eine Teilmenge neu gerechnet wurde. Das UI muss es sagen.
     * @param hits       Wie viele Buchungen der Suchtext getroffen hat.
     * @param hitTotal   Deren Summe, absolut.
     */
    data class Result(
        val mode: Mode,
        val entries: List<CategoryBreakdownEntry>,
        val bookings: List<Booking>,
        val recomputed: Boolean,
        val hits: Int,
        val hitTotal: Double,
    )

    /**
     * @param periodBookings Der Bestand des gewählten Zeitraums — derselbe, aus dem
     *                       [breakdown] gerechnet wurde.
     * @param breakdown      Die ungefilterte Aufschlüsselung, für [Mode.CATEGORY].
     */
    fun apply(
        periodBookings: List<Booking>,
        breakdown: List<CategoryBreakdownEntry>,
        query: String,
        mode: Mode,
        side: CategoryBreakdownSide = CategoryBreakdownSide.EXPENSES,
        filterTransfers: Boolean = true,
    ): Result {
        val relevant = sideBookings(periodBookings, side, filterTransfers)

        if (query.isBlank()) {
            return Result(mode, breakdown, relevant, recomputed = false, hits = relevant.size,
                hitTotal = relevant.sumOf { abs(it.amount) })
        }

        return when (mode) {
            Mode.CATEGORY -> Result(
                mode = mode,
                entries = CategorySearch.filter(breakdown, query),
                bookings = relevant,
                recomputed = false,
                hits = relevant.size,
                hitTotal = relevant.sumOf { abs(it.amount) },
            )

            Mode.DESCRIPTION -> {
                val matched = BookingSearch.filter(relevant, query)
                Result(
                    mode = mode,
                    // Gegen die Treffer gerechnet, nicht gegen den Zeitraum: die Prozente
                    // beantworten dann "wie verteilt sich das Gesuchte", und die Summe der
                    // Zeilen ergibt die Zahl, die darüber steht.
                    entries = calculateCategoryBreakdown(matched, filterTransfers = false, side = side),
                    bookings = matched,
                    recomputed = true,
                    hits = matched.size,
                    hitTotal = matched.sumOf { abs(it.amount) },
                )
            }
        }
    }

    /**
     * Die Buchungen, die in die Aufschlüsselung eingehen — dieselbe Auswahl, die
     * [calculateCategoryBreakdown] intern trifft.
     *
     * Sie hier noch einmal zu treffen ist nötig, weil der Drilldown und die Trefferzahl
     * dieselbe Menge meinen müssen wie die Balken. Zwei Auswahlen, die auseinanderlaufen,
     * wären eine Liste, die gegen ihre eigene Summe nicht aufgeht.
     */
    private fun sideBookings(
        bookings: List<Booking>,
        side: CategoryBreakdownSide,
        filterTransfers: Boolean,
    ): List<Booking> =
        (if (filterTransfers) bookings.excludeTransfers() else bookings)
            .filter { if (side == CategoryBreakdownSide.INCOME) it.amount > 0.0 else it.amount < 0.0 }
}
