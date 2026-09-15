package io.github.willywonka644.fintracker.sync

import io.github.willywonka644.fintracker.ISettingsRepository

/**
 * The address of the sync server and the key that opens it, on the desktop (#92).
 *
 * ## Why app_settings here, when Android deliberately avoided it
 *
 * #89 decided the Android key belongs in DataStore, "wie der PIN" — and
 * explicitly not in `app_settings`. That reasoning is followed here rather than
 * copied: on this platform the PIN lives in `app_settings`
 * ([io.github.willywonka644.fintracker.security.DesktopPinStore]), so this is
 * the same place, reached the same way.
 *
 * The Android decision was driven by Android's automatic cloud backup, which
 * copies the whole app data directory to Google Drive unless told otherwise —
 * which is why #91 had to add exclude rules to two XML files. Nothing on the
 * desktop copies the database off the machine by itself.
 *
 * ## What has to stay true
 *
 * `app_settings` is the one table the sync machinery holds a handle to:
 * [SyncExporter] takes an [ISettingsRepository] — today only to read the device
 * id. The payload carries accounts, bookings, categories and rules, no settings.
 * If that ever changes, this key would be pushed to the server and pulled onto
 * every other device. #89 asked for a test that pins it; that test belongs with
 * this class, not near the exporter, because this is what makes it matter.
 */
class DesktopServerSyncStore(private val settingsRepository: ISettingsRepository) {

    /** The normalised URL, empty while none is configured. */
    val serverUrl: String
        get() = settingsRepository.get(KEY_URL).orEmpty()

    val apiKey: String
        get() = settingsRepository.get(KEY_API_KEY).orEmpty()

    /**
     * Both values at once.
     *
     * One call rather than two reads, for the reason the Android store gives: a
     * sync must not start with the address from before an edit and the key from
     * after it.
     */
    fun current(): ServerSyncConfig = ServerSyncConfig(url = serverUrl, apiKey = apiKey)

    /**
     * Stores what the user entered.
     *
     * The URL is expected to have been through [ServerUrl] already. The key is
     * trimmed because it is pasted, and a trailing newline from the clipboard
     * would otherwise present as "key wrong" with nothing visible to correct.
     */
    fun save(url: String, apiKey: String) {
        settingsRepository.set(KEY_URL, url.trim())
        settingsRepository.set(KEY_API_KEY, apiKey.trim())
    }

    /**
     * Wann zuletzt abgeglichen wurde, in Millisekunden. 0 heisst: noch nie.
     *
     * Ohne diesen Wert startete der Desktop seinen Zaehler offener Aenderungen bei null
     * und meldete nach jedem Programmstart "nichts ausstehend" — auch wenn in der
     * Datenbank ungesyncte Zeilen lagen (#114). Genau dafuer wurde er auf Android
     * eingefuehrt (#110).
     *
     * Liegt bewusst in app_settings: die Tabelle wandert nicht in die Sync-Nutzlast, und
     * "wann habe ICH zuletzt abgeglichen" ist eine Aussage ueber dieses Geraet.
     */
    var lastSyncedAt: Long
        get() = settingsRepository.get(KEY_LAST_SYNCED_AT)?.toLongOrNull() ?: 0L
        set(value) = settingsRepository.set(KEY_LAST_SYNCED_AT, value.toString())

    fun clear() {
        settingsRepository.set(KEY_URL, "")
        settingsRepository.set(KEY_API_KEY, "")
    }

    private companion object {
        const val KEY_URL = "server_sync_url"
        const val KEY_API_KEY = "server_sync_api_key"
        const val KEY_LAST_SYNCED_AT = "server_sync_last_synced_at"
    }
}
