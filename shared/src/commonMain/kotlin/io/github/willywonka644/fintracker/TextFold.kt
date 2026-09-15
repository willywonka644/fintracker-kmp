package io.github.willywonka644.fintracker

/**
 * Wie Text verglichen wird, wenn es auf die Schreibweise nicht ankommen soll.
 *
 * Eine Stelle, weil jede zweite Abschrift eine Gelegenheit ist, dass die Suche in der
 * Auswertung und die Vorschläge im Buchungsformular verschiedene Dinge für gleich
 * halten.
 */
object TextFold {

    /**
     * Kleinschreibung und Umlaute auf ihren Grundbuchstaben — "Lüfter" wird zu "lufter",
     * "Straße" zu "strasse".
     *
     * Die Faltung geht **nur in diese Richtung**: "luf" findet "Lüfter", "luefter" nicht.
     * Das "ue" für "ü" ist eine Gewohnheit von Tastaturen ohne die Taste; diese hat sie.
     */
    fun fold(text: String): String = buildString(text.length) {
        for (ch in text.lowercase()) {
            when (ch) {
                'ä' -> append('a')
                'ö' -> append('o')
                'ü' -> append('u')
                'ß' -> append("ss")
                else -> append(ch)
            }
        }
    }

    /**
     * Die Wörter eines Textes, gefaltet; alles außer a-z und 0-9 trennt.
     *
     * Bewusst eng: was nach der Faltung kein a-z bleibt — das É in "Tech Équipe" etwa —
     * fällt weg. So ist die Regel an den Echtdaten gemessen worden, und eine großzügigere
     * wäre nicht mehr dieselbe Regel.
     */
    fun words(text: String): List<String> =
        NON_ALNUM.split(fold(text)).filter { it.isNotEmpty() }

    /** Alles außer Buchstaben und Ziffern entfernt — "Spar-Rate" und "Sparrate" werden gleich. */
    fun squash(text: String): String = words(text).joinToString("")

    /** Wörter alphabetisch — "ETF Sparrate" und "Sparrate ETF" werden gleich. */
    fun wordKey(text: String): String = words(text).sorted().joinToString("|")

    private val NON_ALNUM = Regex("[^a-z0-9]+")
}
