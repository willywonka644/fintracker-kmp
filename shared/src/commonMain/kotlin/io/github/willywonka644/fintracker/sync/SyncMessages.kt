package io.github.willywonka644.fintracker.sync

/**
 * The sentences the server sync shows when something goes wrong (#91, #92).
 *
 * Here rather than in either app for the same reason [ServerUrl] and
 * [SyncClient] are: four screens across two platforms say the same things about
 * the same failures, and copies of "Der Server ist erreichbar, lehnt den
 * Schlüssel aber ab" would drift apart the first time one of them is reworded.
 *
 * German strings in a shared module is a deliberate trade. Both apps are German
 * and neither is localised, so the alternative is not flexibility but four
 * wordings of one sentence.
 *
 * Every message names a cause the user can act on. That is the point of the
 * issue's demand that errors land in the UI: behind a home router a sync fails
 * for several unrelated reasons, and "Sync fehlgeschlagen" sends people to check
 * the wrong thing.
 */
fun describe(reason: ServerUrl.Reason): String = when (reason) {
    ServerUrl.Reason.EMPTY -> "Bitte eine Server-Adresse eingeben."
    ServerUrl.Reason.WHITESPACE -> "Die Adresse enthält ein Leerzeichen — beim Einfügen mitgekommen?"
    ServerUrl.Reason.BAD_SCHEME -> "Nur http:// und https:// werden unterstützt."
    ServerUrl.Reason.NO_HOST -> "In der Adresse fehlt der Rechnername."
    ServerUrl.Reason.BAD_PORT -> "Der Port muss eine Zahl zwischen 1 und 65535 sein."
    ServerUrl.Reason.MALFORMED -> "Die Adresse lässt sich nicht lesen. IPv6-Adressen in eckige Klammern setzen."
}

fun describe(error: SyncCallError): String = when (error) {
    SyncCallError.UNREACHABLE -> "Keine Antwort. Läuft der Server, und stimmt die Adresse?"
    SyncCallError.TIMEOUT -> "Zeitüberschreitung — der Server meldet sich, aber nicht rechtzeitig."
    SyncCallError.UNAUTHORIZED -> "Der Server lehnt den Schlüssel ab."
    SyncCallError.REJECTED -> "Der Server hat die Anfrage abgelehnt."
    SyncCallError.SERVER_ERROR -> "Der Server meldet einen internen Fehler."
    SyncCallError.UNEXPECTED_STATUS -> "Da antwortet etwas, aber kein FinTracker-Server."
    SyncCallError.MALFORMED_RESPONSE -> "Unlesbare Antwort — vermutlich eine Anmeldeseite des Netzwerks."
}

/**
 * Failures of the merge itself, as opposed to the transport.
 *
 * These mean the exchange worked and the payload was the problem, so they must
 * not be worded like a connection fault — otherwise the user restarts the Pi
 * over a malformed record.
 */
fun describe(error: SyncError): String = when (error) {
    SyncError.InvalidJson -> "Die Antwort des Servers ist kein gültiges JSON."
    SyncError.UnknownVersion -> "Die Daten stammen aus einer anderen App-Version."
    is SyncError.MissingField -> "In den Daten fehlt das Feld „${error.fieldName}“."
    SyncError.WriteFailed -> "Die Daten konnten nicht gespeichert werden."
}
