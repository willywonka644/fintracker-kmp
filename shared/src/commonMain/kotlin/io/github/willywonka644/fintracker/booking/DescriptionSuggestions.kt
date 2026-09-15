package io.github.willywonka644.fintracker.booking

import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.BookingSource
import io.github.willywonka644.fintracker.TextFold

/**
 * Schlägt beim Tippen eine Beschreibung vor, die es schon gibt — und die Kategorie dazu (#136).
 *
 * Am Echtbestand vom 15.09.2026 gemessen, chronologisch durchgespielt, also mit genau dem
 * Wissen, das im Moment der Eingabe da gewesen wäre:
 * - **40 %** aller Buchungen wiederholen eine Beschreibung wortgleich — der Vorschlag hätte
 *   den fertigen Text gehabt.
 * - Weitere **31 %** wären nach drei Anfangsbuchstaben in der Liste gestanden.
 * - Aus einer bekannten Beschreibung wäre die Kategorie in **87 %** richtig geraten worden.
 *
 * Die zweite Wirkung zählt genauso: wer "ETF Spar" tippt und den Vorschlag nimmt, erfindet
 * keine vierte Schreibweise. Das ist die Vorbeugung zu dem, was [DescriptionCleanup]
 * nachträglich aufräumt.
 */
object DescriptionSuggestions {

    /** Wie viele Vorschläge höchstens angeboten werden. Mehr als das liest niemand. */
    const val DEFAULT_LIMIT = 5

    /**
     * Ein Vorschlag: der Text, wie er zuletzt geschrieben wurde, und die Kategorie, die
     * am häufigsten dazu gehörte.
     *
     * @param description  Der vorzuschlagende Text.
     * @param category     Häufigste Kategorie zu diesem Text, `null` wenn es keine gab.
     * @param uses         Wie oft der Text vorkam — die Begründung der Reihenfolge.
     */
    data class Suggestion(
        val description: String,
        val category: String?,
        val uses: Int,
    )

    /**
     * Der Vorrat, aus dem [forQuery] schöpft. Einmal je Buchungsbestand bauen, nicht je
     * Tastendruck.
     *
     * Draußen bleiben:
     * - **Ratenzahlungen** ([Booking.installmentGroupId] gesetzt). Deren " 7/12"-Texte
     *   erzeugt das Raten-Werkzeug; zwölf Raten würden die Liste verstopfen mit etwas,
     *   das von Hand ohnehin nie so eingegeben wird.
     * - **Kontoabgleich-Korrekturen** ([BookingSource.RECONCILIATION]) — technische Zeilen.
     * - Gelöschte und leere Texte.
     *
     * Dauerauftrag-Buchungen bleiben drin: deren Text hat jemand einmal geschrieben, und
     * dass er oft vorkommt, ist keine Verzerrung, sondern der Befund.
     */
    fun index(bookings: List<Booking>): List<Suggestion> {
        val relevant = bookings.filter {
            !it.deleted &&
                it.installmentGroupId == null &&
                it.source != BookingSource.RECONCILIATION &&
                it.description.isNotBlank()
        }
        if (relevant.isEmpty()) return emptyList()

        return relevant
            .groupBy { TextFold.fold(it.description.trim()) }
            .map { (_, group) ->
                val byRecency = group.sortedByDescending { it.effectiveDate ?: it.timestamp }
                Suggestion(
                    // Die zuletzt geschriebene Schreibweise gewinnt: wer gerade eben
                    // "Spotify" korrigiert hat, soll nicht das alte "spotify" angeboten
                    // bekommen.
                    description = byRecency.first().description.trim(),
                    category = dominantCategory(group),
                    uses = group.size,
                )
            }
            .sortedWith(compareByDescending<Suggestion> { it.uses }.thenBy { it.description })
    }

    /**
     * Die Vorschläge zu [query], höchstens [limit] Stück.
     *
     * Ein leerer Suchtext liefert die häufigsten überhaupt — beim Öffnen des Feldes ist das
     * die beste Vermutung, die man ohne Eingabe machen kann. Sonst zählt jedes Vorkommen
     * des Textes irgendwo im Namen, nicht nur am Anfang: "sparrate" soll "ETF Sparrate"
     * finden.
     *
     * Sortiert wird allein nach Häufigkeit. Ein Bonus für Treffer am Wortanfang wurde
     * erwogen und verworfen: er hätte bei "spar" das dreimal benutzte "Sparrate" über das
     * sechsmal benutzte "ETF Sparrate" gehoben, und eine Reihenfolge, die sich nicht in
     * einem Satz erklären lässt, wirkt zufällig.
     *
     * Ein Text, der schon **genau** dasteht, wird nicht mehr vorgeschlagen — ihn
     * anzubieten hieße, dem Nutzer das anzubieten, was er gerade getippt hat.
     */
    fun forQuery(
        index: List<Suggestion>,
        query: String,
        limit: Int = DEFAULT_LIMIT,
    ): List<Suggestion> {
        val needle = TextFold.fold(query.trim())
        if (needle.isEmpty()) return index.take(limit)
        return index
            .filter { TextFold.fold(it.description).let { d -> d.contains(needle) && d != needle } }
            .take(limit)
    }

    /**
     * Die Kategorie, die zu diesen Buchungen am häufigsten gehört.
     *
     * Bei Gleichstand gewinnt die **jüngere** — wer eine Kategorie gerade gewechselt hat,
     * meint eher die neue als die alte. Buchungen ohne Kategorie stimmen nicht mit ab;
     * sonst hätte ein Text, der zweimal ohne und einmal mit Kategorie vorkam, gar keinen
     * Vorschlag, obwohl einer dasteht.
     */
    private fun dominantCategory(group: List<Booking>): String? {
        val withCategory = group.mapNotNull { b ->
            b.category?.trim()?.takeIf { it.isNotEmpty() }?.let { it to (b.effectiveDate ?: b.timestamp) }
        }
        if (withCategory.isEmpty()) return null
        return withCategory
            .groupBy { it.first }
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<String, List<Pair<String, Long>>>> { it.value.size }
                    .thenByDescending { entry -> entry.value.maxOf { it.second } }
            )
            .first().key
    }
}
