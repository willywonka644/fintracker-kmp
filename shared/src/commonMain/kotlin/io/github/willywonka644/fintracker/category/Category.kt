package io.github.willywonka644.fintracker.category

import kotlinx.serialization.Serializable
import kotlin.random.Random

private fun randomUuid(): String {
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

/**
 * Persistent category model.
 *
 * @param id        Unique identifier (UUID string).
 * @param name      Display name, e.g. "Lebensmittel".
 * @param iconName  Material icon name, e.g. "ShoppingCart". Resolved at UI level.
 * @param color     ARGB color stored as a Long (e.g. 0xFFFFA726).
 * @param isDefault True for the built-in categories that ship with the app.
 * @param deleted   Tombstone flag. The sync merge is a union by id and never drops
 *                  a record, so a deletion only propagates if it travels as a row.
 */
@Serializable
data class Category(
    val id: String = randomUuid(),
    val name: String,
    val iconName: String,
    val color: Long,
    val isDefault: Boolean = false,
    val lastModifiedAt: Long = 0L,
    val deleted: Boolean = false
)
