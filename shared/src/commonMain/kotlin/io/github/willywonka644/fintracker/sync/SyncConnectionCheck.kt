package io.github.willywonka644.fintracker.sync

/** The sentence to show, and whether it is good news. */
data class ConnectionCheck(val message: String, val ok: Boolean)

/**
 * Checks address and key, and says which of them is wrong (#91, #92).
 *
 * Shared rather than written once per platform: this is not rendering, it is the
 * order in which causes are ruled out, and two versions of that order would
 * disagree about what a failure means.
 *
 * ## The order is the point
 *
 * 1. **The address, before anything leaves the device.** A typo needs no network
 *    to be recognised, and [ServerUrl] already names which typo it is.
 * 2. **`/health`, without the key.** The server leaves this one route open, and
 *    that is what separates "nothing there" from "there, but rejects the key".
 * 3. **The key**, and only then.
 *
 * Because step 2 ran first, a rejected key can be reported as "the server is
 * reachable and refuses the key" instead of something that sends the user off to
 * check whether the Pi is even running.
 *
 * ## Why the key check downloads
 *
 * There is no cheaper authenticated route: the guard denies by default (#89), so
 * a made-up path answers 401 as well and would report a wrong key for a correct
 * one. [SyncClient.pull] is used and its body discarded. On a button pressed
 * rarely that is the better trade, and it exercises the path a real sync takes.
 * If the dataset ever grows enough for that to hurt, the fix belongs on the
 * server as a small authenticated status route.
 *
 * Does not choose a dispatcher — the caller knows whether it is on a UI thread.
 */
suspend fun checkSyncConnection(
    client: SyncClient,
    rawUrl: String,
    apiKey: String,
): ConnectionCheck {
    val url = when (val normalized = ServerUrl.normalize(rawUrl)) {
        is ServerUrl.Result.Invalid -> return ConnectionCheck(describe(normalized.reason), ok = false)
        is ServerUrl.Result.Valid -> normalized.url
    }

    if (apiKey.isBlank()) return ConnectionCheck("Es fehlt der API-Schlüssel.", ok = false)

    return when (val health = client.health(url)) {
        is SyncCallResult.Failed -> ConnectionCheck("${describe(health.error)} ($url)", ok = false)

        is SyncCallResult.Ok -> when (val pull = client.pull(url, apiKey.trim())) {
            is SyncCallResult.Ok ->
                ConnectionCheck("Verbindung steht. $url antwortet und akzeptiert den Schlüssel.", ok = true)

            is SyncCallResult.Failed -> when (pull.error) {
                // The distinction this whole function exists for: /health just
                // answered, so the server is demonstrably there and this can
                // only be the key.
                SyncCallError.UNAUTHORIZED ->
                    ConnectionCheck("Der Server ist erreichbar, lehnt den Schlüssel aber ab.", ok = false)

                else -> ConnectionCheck(describe(pull.error), ok = false)
            }
        }
    }
}
