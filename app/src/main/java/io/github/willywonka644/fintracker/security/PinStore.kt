package io.github.willywonka644.fintracker.security

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.pinDataStore by preferencesDataStore(name = "pin_store")

class PinStore(private val context: Context) {
    private val dataStore = context.pinDataStore

    private object Keys {
        val PIN_ENABLED = booleanPreferencesKey("pin_enabled")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val PIN_HASH = stringPreferencesKey("pin_hash")
    }

    val pinEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.PIN_ENABLED] ?: false
    }

    suspend fun setPin(pin: String) {
        val salt = PinHasher.generateSalt()
        val hash = PinHasher.hashPin(pin, salt)
        dataStore.edit { prefs ->
            prefs[Keys.PIN_ENABLED] = true
            prefs[Keys.PIN_SALT] = salt
            prefs[Keys.PIN_HASH] = hash
        }
    }

    suspend fun disablePin() {
        dataStore.edit { prefs ->
            prefs[Keys.PIN_ENABLED] = false
            prefs.remove(Keys.PIN_SALT)
            prefs.remove(Keys.PIN_HASH)
        }
    }

    suspend fun verifyPin(pin: String): Boolean {
        val prefs: Preferences = dataStore.data.first()
        val enabled = prefs[Keys.PIN_ENABLED] ?: false
        if (!enabled) return true

        val salt = prefs[Keys.PIN_SALT] ?: return false
        val hash = prefs[Keys.PIN_HASH] ?: return false
        val computed = PinHasher.hashPin(pin, salt)
        return computed == hash
    }
}
