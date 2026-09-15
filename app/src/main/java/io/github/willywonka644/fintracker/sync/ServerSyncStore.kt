package io.github.willywonka644.fintracker.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The address of the Pi and the API key that opens it (#91).
 *
 * Kept in DataStore, the same place the PIN lives ([io.github.willywonka644.fintracker.security.PinStore]),
 * rather than in the `app_settings` table. The decision and its reasoning are in
 * #89; the short version is that the key is not worth an Android Keystore
 * treatment while the database it protects sits unencrypted beside it, but it is
 * worth keeping out of every automatic copy of the app.
 *
 * ## Why the store name matters
 *
 * [STORE_NAME] appears verbatim in `res/xml/backup_rules.xml` and
 * `res/xml/data_extraction_rules.xml`, which exclude this file from Android's
 * automatic backup. `allowBackup` is on, so without those two rules the key would
 * travel to Google Drive with the rest of the app data — found while working on
 * #89. **Renaming this store silently re-enables that**, because an exclude rule
 * that matches nothing is not an error.
 */
class ServerSyncStore(private val context: Context) {

    private val dataStore = context.serverSyncDataStore

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val API_KEY = stringPreferencesKey("api_key")
        val LAST_SYNCED_AT = longPreferencesKey("last_synced_at")
    }

    /**
     * When the last successful sync finished, or 0 while none ever has (#110).
     *
     * Lives here rather than as a counter in memory because the overview's sync button
     * claims a state permanently: a counter starts at zero on every app start and would
     * report "nothing outstanding" while something is. Being excluded from Android's
     * automatic backup along with the key is right — a restored app has not synced.
     */
    val lastSyncedAt: Flow<Long> = dataStore.data.map { it[Keys.LAST_SYNCED_AT] ?: 0L }

    /** Records that everything up to [atMillis] has reached the server. */
    suspend fun markSynced(atMillis: Long) {
        dataStore.edit { prefs -> prefs[Keys.LAST_SYNCED_AT] = atMillis }
    }

    /** The normalised URL, or null while none is configured. */
    val serverUrl: Flow<String?> = dataStore.data.map { it[Keys.SERVER_URL]?.takeIf(String::isNotBlank) }

    /** True once both address and key are present — the condition for offering a sync. */
    val isConfigured: Flow<Boolean> = dataStore.data.map { prefs ->
        !prefs[Keys.SERVER_URL].isNullOrBlank() && !prefs[Keys.API_KEY].isNullOrBlank()
    }

    /**
     * Address and key as a stream, for screens that stay on the display while the
     * values can change underneath them.
     *
     * The sync screen needs this: the settings screen is reachable from it and is
     * drawn on top, so the sync screen is never recomposed from scratch when the
     * user comes back. A one-shot [current] read there left it showing — and
     * syncing against — the address from before the edit.
     */
    val config: Flow<ServerSyncConfig> = dataStore.data.map { prefs ->
        ServerSyncConfig(
            url = prefs[Keys.SERVER_URL].orEmpty(),
            apiKey = prefs[Keys.API_KEY].orEmpty(),
        )
    }

    /**
     * Reads both values at once.
     *
     * One read rather than two, so a sync cannot start with the address from
     * before an edit and the key from after it.
     */
    suspend fun current(): ServerSyncConfig {
        val prefs = dataStore.data.first()
        return ServerSyncConfig(
            url = prefs[Keys.SERVER_URL].orEmpty(),
            apiKey = prefs[Keys.API_KEY].orEmpty(),
        )
    }

    /**
     * Stores what the user entered.
     *
     * The URL is expected to be normalised by [ServerUrl] beforehand: this is the
     * place that would otherwise accumulate a second, slightly different idea of
     * what a valid address looks like.
     *
     * The key is trimmed because it is pasted, and a trailing newline from a
     * clipboard would otherwise present as "key wrong" with nothing visible to
     * correct.
     */
    suspend fun save(url: String, apiKey: String) {
        dataStore.edit { prefs ->
            prefs[Keys.SERVER_URL] = url.trim()
            prefs[Keys.API_KEY] = apiKey.trim()
        }
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.SERVER_URL)
            prefs.remove(Keys.API_KEY)
            // The marker belongs to the server that was configured. Keeping it would
            // claim a sync against an address that is gone.
            prefs.remove(Keys.LAST_SYNCED_AT)
        }
    }

    companion object {
        /** Must stay in step with the exclude rules in the two backup XML files. */
        const val STORE_NAME = "server_sync"
    }
}

private val Context.serverSyncDataStore by preferencesDataStore(name = ServerSyncStore.STORE_NAME)
