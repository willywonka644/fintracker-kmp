package io.github.willywonka644.fintracker.sync

/** Where one exchange got to. */
sealed interface SyncExchange {
    /**
     * The server took the local data and answered with its own. Nothing has been
     * written locally yet — that step belongs to the person at the device.
     */
    data class NeedsConfirmation(val preview: SyncPreview, val pushed: PushSummary) : SyncExchange

    data class Failed(val message: String) : SyncExchange
}

/**
 * One exchange with the sync server, up to the point where a human has to decide
 * (#91, #92).
 *
 * ## Push first, then pull
 *
 * The local dataset goes up, the server merges it and writes, and only then does
 * this device fetch the merged result — so one exchange leaves both sides
 * holding the union. Pulling first would leave everything changed here since the
 * last sync sitting on this device for another round.
 *
 * ## It stops before writing
 *
 * The return value is a preview, never a completed import. The confirmation
 * dialog from Phase 5 exists so that nothing writes to a device unannounced, and
 * a server that is always reachable makes that more important rather than less:
 * with the Wi-Fi sync a counterpart had to be on the same network at the same
 * moment, and that accident of timing was quietly doing part of the guarding.
 *
 * The push half is deliberately not confirmed. That is this device's own data
 * going out, and #88 decided the server writes immediately because it has nobody
 * to ask.
 *
 * Shared rather than written per platform for the same reason as
 * [checkSyncConnection]: this is an order of operations, and two versions of it
 * would eventually disagree about which side wins.
 *
 * Does not choose a dispatcher — the caller knows whether it is on a UI thread.
 * [onStep] reports progress for the UI and is called before each leg.
 *
 * Takes [exportPayload] and [importPayload] as functions rather than a
 * [SyncExporter] and a [SyncImporter]: this function decides the order of
 * operations, not who produces the JSON. Callers pass `exporter::export` and
 * `importer::import`; a test passes something that needs no database, which is
 * what lets the ordering itself be pinned down.
 */
suspend fun runSyncExchange(
    client: SyncClient,
    config: ServerSyncConfig,
    exportPayload: () -> String,
    importPayload: (String) -> SyncImportResult,
    onStep: (String) -> Unit = {},
): SyncExchange {
    val url = when (val normalized = ServerUrl.normalize(config.url)) {
        is ServerUrl.Result.Invalid -> return SyncExchange.Failed(describe(normalized.reason))
        is ServerUrl.Result.Valid -> normalized.url
    }
    val key = config.apiKey.trim()

    onStep("Daten werden gesendet …")
    val pushed = when (val result = client.push(url, key, exportPayload())) {
        is SyncCallResult.Failed -> return SyncExchange.Failed(describe(result.error))
        is SyncCallResult.Ok -> result.value
    }

    onStep("Daten werden geholt …")
    val body = when (val result = client.pull(url, key)) {
        is SyncCallResult.Failed -> return SyncExchange.Failed(describe(result.error))
        is SyncCallResult.Ok -> result.value
    }

    onStep("Änderungen werden geprüft …")
    return when (val imported = importPayload(body)) {
        is SyncImportResult.Error -> SyncExchange.Failed(describe(imported.error))
        is SyncImportResult.Preview -> SyncExchange.NeedsConfirmation(imported.preview, pushed)
    }
}
