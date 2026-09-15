package io.github.willywonka644.fintracker.security

import io.github.willywonka644.fintracker.ISettingsRepository

/**
 * Desktop implementation of PIN protection backed by [ISettingsRepository] (app_settings table).
 *
 * Keys stored:
 *  - "pin_enabled"  → "true" / "false"
 *  - "pin_salt"     → base64 salt
 *  - "pin_hash"     → base64 hash
 */
class DesktopPinStore(private val settingsRepository: ISettingsRepository) {

    val isPinEnabled: Boolean
        get() = settingsRepository.get(KEY_ENABLED) == "true"

    fun setPin(pin: String) {
        val salt = PinHasher.generateSalt()
        val hash = PinHasher.hashPin(pin, salt)
        settingsRepository.set(KEY_ENABLED, "true")
        settingsRepository.set(KEY_SALT, salt)
        settingsRepository.set(KEY_HASH, hash)
    }

    fun disablePin() {
        settingsRepository.set(KEY_ENABLED, "false")
        settingsRepository.set(KEY_SALT, "")
        settingsRepository.set(KEY_HASH, "")
    }

    fun verifyPin(pin: String): Boolean {
        if (!isPinEnabled) return true
        val salt = settingsRepository.get(KEY_SALT) ?: return false
        val hash = settingsRepository.get(KEY_HASH) ?: return false
        return PinHasher.hashPin(pin, salt) == hash
    }

    private companion object {
        const val KEY_ENABLED = "pin_enabled"
        const val KEY_SALT    = "pin_salt"
        const val KEY_HASH    = "pin_hash"
    }
}
