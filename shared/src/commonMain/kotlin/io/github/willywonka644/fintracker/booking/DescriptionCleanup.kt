package io.github.willywonka644.fintracker.booking

import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.TextFold

/**
 * Findet Buchungen, die dasselbe meinen und verschieden heißen (#135).
 *
 * Im Echtbestand stehen 465 Buchungen unter 334 verschiedenen Beschreibungen: "ETF
 * Sparrate" neben "Sparrate ETF", "Bargeldabhebung Automat" neben "Bargeldabhenung Automat".
 * Das kostet keine Summe, aber jede Suche und jede Auswertung darüber.
 *
 * **Es wird immer nur vorgeschlagen.** Welche zwei Texte dasselbe meinen, ist eine Aussage
 * über Absicht, und die trifft der Mensch — Gruppe für Gruppe, nie in einem Rutsch. Das ist
 * dieselbe Zurückhaltung wie bei [TransferSuggestions], und sie ist hier gemessen begründet:
 *
 * Eine dritte Stufe "ein Text ist Teilmenge des anderen" war gebaut und ist an den Echtdaten
 * gescheitert. Sie verkettete sich transitiv zu Klumpen — 21 Schreibweisen von einer
 * Bargeldabhebung bis zu einer Rückzahlung an eine Privatperson in **einer** Gruppe,
 * 23 rund um einen Händlernamen samt "Kreditkartenabrechnung", zusammen 185 der 394
 * Buchungen. Verworfen.
 * Ob "Sparrate" und "ETF Sparrate" dasselbe sind, weiß nur der Nutzer; dafür gibt es das
 * Gruppieren von Hand, nicht die Automatik.
 *
 * Was bleibt, traf am 15.09.2026 **10 Gruppen mit 47 Buchungen und keinen Fehltreffer**.
 */
object DescriptionCleanup {

    /** Warum eine Gruppe vorgeschlagen wird. Steht im UI, damit der Vorschlag prüfbar ist. */
    enum class Reason {
        /** Identisch bis auf Groß-/Kleinschreibung, Leerzeichen, Satzzeichen, Wortreihenfolge. */
        SPELLING,

        /** Bis auf ein, zwei Buchstaben identisch — "Bargeldabhenung", "Musikaboo". */
        TYPO,
    }

    /** Eine Schreibweise innerhalb einer Gruppe. */
    data class Variant(
        val description: String,
        val uses: Int,
        val bookingIds: List<String>,
    )

    /**
     * Ein Vorschlag: mehrere Schreibweisen, die dasselbe meinen.
     *
     * @param variants   Häufigste zuerst — die erste ist der Vorschlag für [suggestedTarget].
     * @param categories Welche Kategorien in der Gruppe vorkommen, mit ihrer Häufigkeit.
     *                   Mehr als eine heißt: hier verfälscht die Uneinheitlichkeit auch
     *                   die Auswertung, nicht nur die Optik.
     */
    data class Group(
        val key: String,
        val reason: Reason,
        val variants: List<Variant>,
        val categories: Map<String, Int>,
    ) {
        /** Die häufigste Schreibweise — bei Gleichstand die alphabetisch erste. */
        val suggestedTarget: String get() = variants.first().description

        val totalUses: Int get() = variants.sumOf { it.uses }

        val categoriesDisagree: Boolean get() = categories.size > 1
    }

    /**
     * Wörter, die Buchungen absichtlich unterscheiden: Monate und Zahlen.
     *
     * Ohne diese Liste ist "Gehalt Juni" / "Gehalt Juli" ein Tippfehler von genau einem
     * Buchstaben, und "Bargeldabhebung PASSPORT 1" / "… PASSPORT 2" von zweien. Beide
     * wurden beim Messen tatsächlich vorgeschlagen, bevor die Regel sie ausnahm.
     */
    private val VARIANT_MARKERS = setOf(
        "januar", "februar", "marz", "april", "mai", "juni", "juli", "august",
        "september", "oktober", "november", "dezember",
        "jan", "feb", "mrz", "apr", "jun", "jul", "aug", "sep", "okt", "nov", "dez",
    )

    private fun isVariantMarker(word: String) =
        word in VARIANT_MARKERS || word.all { it.isDigit() }

    private fun markers(words: List<String>) = words.filter { isVariantMarker(it) }.sorted()

    /**
     * Die Gruppen im Bestand, die größte zuerst.
     *
     * Ratenzahlungen bleiben draußen: ihre " 7/12"-Texte erzeugt das Raten-Werkzeug, und
     * eine Umbenennung dort gehört in den Raten-Dialog, nicht hierher.
     *
     * [rejected] enthält [Group.key]s, die der Nutzer schon weggeschickt hat — ein Vorschlag,
     * der nach dem Ablehnen wiederkommt, ist keine Hilfe mehr, sondern eine Mahnung.
     */
    fun suggest(bookings: List<Booking>, rejected: Set<String> = emptySet()): List<Group> {
        val relevant = bookings.filter {
            !it.deleted && it.installmentGroupId == null && it.description.isNotBlank()
        }
        if (relevant.isEmpty()) return emptyList()

        val byDescription = relevant.groupBy { it.description.trim() }
        val names = byDescription.keys.sorted()
        if (names.size < 2) return emptyList()

        val union = UnionFind(names)
        // Der Grund haengt am Namen, nicht an der Wurzel: die Wurzel wandert beim
        // Verschmelzen, und ein Grund, der unter einer alten Wurzel abgelegt wurde,
        // waere am Ende nicht mehr auffindbar.
        val reasonOf = mutableMapOf<String, Reason>()

        // Stufe 1 — identisch nach Normalisierung. Zwei Schlüssel, weil keiner allein
        // reicht: der Wortschlüssel fängt die Reihenfolge ("ETF Sparrate" / "Sparrate
        // ETF"), der zusammengezogene die Satzzeichen ("Spar-Rate" / "Sparrate").
        for (keyOf in listOf(TextFold::wordKey, TextFold::squash)) {
            names.groupBy { keyOf(it) }.values.filter { it.size > 1 }.forEach { bucket ->
                bucket.forEach { name -> reasonOf.getOrPut(name) { Reason.SPELLING } }
                bucket.drop(1).forEach { other -> union.join(bucket.first(), other) }
            }
        }

        // Stufe 2 — Tippfehler. Nur bei gleicher Wortzahl, gleichen Monats- und Zahlwörtern
        // und mindestens sechs Zeichen; unter sechs ist ein Abstand von zwei kein
        // Verschreiber mehr, sondern ein anderes Wort.
        for (i in names.indices) {
            val a = names[i]
            val wordsA = TextFold.words(a)
            val squashA = TextFold.squash(a)
            if (squashA.length < 6) continue
            for (j in (i + 1) until names.size) {
                val b = names[j]
                if (union.root(a) == union.root(b)) continue
                val wordsB = TextFold.words(b)
                if (wordsA.size != wordsB.size) continue
                if (markers(wordsA) != markers(wordsB)) continue
                if (levenshteinAtMost(squashA, TextFold.squash(b), 2)) {
                    reasonOf[a] = Reason.TYPO
                    reasonOf[b] = Reason.TYPO
                    union.join(a, b)
                }
            }
        }

        return names.groupBy { union.root(it) }
            .filterValues { it.size > 1 }
            .map { (_, group) ->
                val variants = group
                    .map { name ->
                        val rows = byDescription.getValue(name)
                        Variant(name, rows.size, rows.map { it.id })
                    }
                    .sortedWith(compareByDescending<Variant> { it.uses }.thenBy { it.description })
                val categories = group
                    .flatMap { byDescription.getValue(it) }
                    .mapNotNull { it.category?.trim()?.takeIf { c -> c.isNotEmpty() } }
                    .groupingBy { it }.eachCount()
                Group(
                    key = groupKey(variants.map { it.description }),
                    // Eine Gruppe, in der auch nur ein Paar ein Verschreiber ist, heisst
                    // Tippfehler: das ist der Grund, den der Nutzer pruefen muss.
                    reason = if (group.any { reasonOf[it] == Reason.TYPO }) Reason.TYPO
                             else Reason.SPELLING,
                    variants = variants,
                    categories = categories,
                )
            }
            .filter { it.key !in rejected }
            .sortedWith(compareByDescending<Group> { it.totalUses }.thenBy { it.suggestedTarget })
    }

    /**
     * Die Identität einer Gruppe, unabhängig davon, in welcher Reihenfolge sie gefunden
     * wurde — damit ein Ablehnen auch dann noch gilt, wenn später eine Buchung dazukommt.
     */
    fun groupKey(descriptions: List<String>): String =
        descriptions.map { TextFold.fold(it) }.sorted().joinToString("|")

    fun parseRejected(raw: String?): Set<String> =
        raw?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

    fun withRejection(raw: String?, key: String): String =
        (parseRejected(raw) + key).sorted().joinToString("\n")

    /**
     * Schreibt [target] in alle Buchungen der Gruppe und gibt den **ganzen** Bestand zurück.
     *
     * [now] landet in `lastModifiedAt` — ohne das nimmt der Abgleich die Umbenennung nicht
     * mit, und das andere Gerät schreibt beim nächsten Sync den alten Text zurück. Das ist
     * hier die einzige Falle, die Daten kostet.
     *
     * Buchungen, die schon [target] heißen, bleiben unangetastet: eine Umbenennung, die
     * nichts ändert, soll auch nicht als Änderung zu senden sein.
     *
     * Eine Kategorie wird **nicht** mitgeschrieben. Dass zwei Schreibweisen dasselbe meinen,
     * heißt nicht, dass eine der beiden Kategorien die falsche ist — das UI zeigt die
     * Abweichung an, entscheiden muss sie jemand anderes.
     */
    fun apply(
        bookings: List<Booking>,
        group: Group,
        target: String,
        now: Long,
    ): List<Booking> {
        val clean = target.trim()
        require(clean.isNotEmpty()) { "Ein leerer Zieltext benennt nichts um." }
        val ids = group.variants.flatMap { it.bookingIds }.toSet()
        return bookings.map { booking ->
            if (booking.id in ids && booking.description.trim() != clean) {
                booking.copy(description = clean, lastModifiedAt = now)
            } else {
                booking
            }
        }
    }

    /** Wie viele Buchungen [apply] tatsächlich anfassen würde. */
    fun affectedCount(bookings: List<Booking>, group: Group, target: String): Int {
        val clean = target.trim()
        val ids = group.variants.flatMap { it.bookingIds }.toSet()
        return bookings.count { it.id in ids && it.description.trim() != clean }
    }

    /**
     * Ob sich [a] und [b] mit höchstens [max] Einfügungen, Löschungen oder Ersetzungen
     * ineinander überführen lassen.
     *
     * Bricht früh ab, sobald schon der Längenunterschied zu groß ist — bei 334 Namen wären
     * das sonst 55.000 volle Vergleiche bei jedem Aufruf.
     */
    internal fun levenshteinAtMost(a: String, b: String, max: Int): Boolean {
        if (a == b) return true
        if (a.length - b.length > max || b.length - a.length > max) return false
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            var rowMin = current[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
                if (current[j] < rowMin) rowMin = current[j]
            }
            if (rowMin > max) return false
            val swap = previous; previous = current; current = swap
        }
        return previous[b.length] <= max
    }

    /** Verschmilzt Namen zu Gruppen. Klein gehalten; der Bestand ist einige Hundert Namen. */
    private class UnionFind(names: List<String>) {
        private val parent = names.associateWith { it }.toMutableMap()

        fun root(name: String): String {
            var current = name
            while (parent.getValue(current) != current) {
                val grandparent = parent.getValue(parent.getValue(current))
                parent[current] = grandparent
                current = grandparent
            }
            return current
        }

        fun join(a: String, b: String) {
            val rootA = root(a)
            val rootB = root(b)
            if (rootA != rootB) parent[rootA] = rootB
        }
    }
}
