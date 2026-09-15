package io.github.willywonka644.fintracker.booking

import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.TextFold

/**
 * Buchungen nach Bezeichnung **oder** Kategorie suchen (#137).
 *
 * Es gab das schon — aber nur im Desktop-Dashboard, mit einem eigenen `contains` vor Ort.
 * Damit hätte die Buchungen-Seite eine zweite Suchregel bekommen und die Auswertungen eine
 * dritte, und drei Suchfelder, die auf dieselbe Eingabe verschieden antworten, sind
 * schlimmer als eines weniger.
 *
 * Ein Feld für beides, weil die Frage im Kopf nicht getrennt ist: „was war das mit dem
 * Kaffee" kann die Bezeichnung meinen oder die Kategorie, und wer erst wählen muss, wonach
 * er sucht, weiß die Antwort meist schon.
 *
 * Gegenüber dem Dashboard-Original kommt die Umlaut-Faltung aus [TextFold] dazu: „luf"
 * findet „Lüfter".
 */
object BookingSearch {

    /**
     * Ob [booking] zu [query] passt. Ein leerer Suchtext passt auf alles — ein Filter, der
     * nichts einschränkt, schränkt nichts ein.
     *
     * Gesucht wird in [Booking.description] und [Booking.category]. **Nicht** in
     * `rawDescription` oder `merchantName`: die stammen aus dem CSV-Import, stehen nirgends
     * auf dem Schirm, und ein Treffer in einem unsichtbaren Feld sieht aus wie ein Fehler.
     */
    fun matches(booking: Booking, query: String): Boolean {
        val needle = TextFold.fold(query.trim())
        if (needle.isEmpty()) return true
        if (TextFold.fold(booking.description).contains(needle)) return true
        val category = booking.category
        return category != null && TextFold.fold(category).contains(needle)
    }

    /** Die Buchungen, die zu [query] passen, in der gegebenen Reihenfolge. */
    fun filter(bookings: List<Booking>, query: String): List<Booking> =
        if (query.isBlank()) bookings else bookings.filter { matches(it, query) }

    /**
     * Die Kategorienamen, unter denen [bookings] zu [query] passende Buchungen haben.
     *
     * Für die Auswertungen: dort ist die Frage nicht „welche Buchung", sondern „welche
     * Kategorie-Zeile hat überhaupt etwas mit dem Gesuchten zu tun".
     */
    fun matchingCategories(bookings: List<Booking>, query: String): Set<String> =
        filter(bookings, query)
            .map { it.category?.trim()?.takeIf { c -> c.isNotEmpty() } ?: "Ohne Kategorie" }
            .toSet()
}
