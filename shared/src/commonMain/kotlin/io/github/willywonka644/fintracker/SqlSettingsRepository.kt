package io.github.willywonka644.fintracker

import io.github.willywonka644.fintracker.db.FintrackerDatabase
import kotlin.random.Random

private const val KEY_DEVICE_ID = "deviceId"

class SqlSettingsRepository(private val db: FintrackerDatabase) : ISettingsRepository {

    private val queries = db.app_settingsQueries

    init {
        if (queries.get(KEY_DEVICE_ID).executeAsOneOrNull() == null) {
            queries.set(KEY_DEVICE_ID, generateUuid())
        }
    }

    override fun getDeviceId(): String =
        queries.get(KEY_DEVICE_ID).executeAsOne()

    override fun get(key: String): String? =
        queries.get(key).executeAsOneOrNull()

    override fun set(key: String, value: String) {
        queries.set(key, value)
    }
}

private fun generateUuid(): String {
    val bytes = ByteArray(16).also { Random.nextBytes(it) }
    bytes[6] = (bytes[6].toInt() and 0x0f or 0x40).toByte()
    bytes[8] = (bytes[8].toInt() and 0x3f or 0x80).toByte()
    return buildString {
        bytes.forEachIndexed { i, b ->
            if (i in intArrayOf(4, 6, 8, 10)) append('-')
            append(b.toInt().and(0xff).toString(16).padStart(2, '0'))
        }
    }
}
