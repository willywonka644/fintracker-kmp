package io.github.willywonka644.fintracker

interface ISettingsRepository {
    fun getDeviceId(): String
    fun get(key: String): String?
    fun set(key: String, value: String)
}
