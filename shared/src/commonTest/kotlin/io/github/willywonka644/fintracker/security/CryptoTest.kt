package io.github.willywonka644.fintracker.security

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The six platform functions behind the sync (#102). Android and desktop share one
 * implementation now, but the sync only works as long as what one device writes the
 * other can read back — so the round trip and the wire format are pinned here rather
 * than left to the fact that there is currently only one copy.
 */
class CryptoTest {

    private val key = ByteArray(32) { it.toByte() }

    @Test
    fun aesGcm_decryptsWhatItEncrypted() {
        val plaintext = "Volksbank Girokonto — Umlaute, €, and a \u0000 byte".encodeToByteArray()
        val restored = aesGcmDecrypt(aesGcmEncrypt(plaintext, key), key)
        assertContentEquals(plaintext, restored)
    }

    @Test
    fun aesGcm_survivesAnEmptyPayload() {
        assertContentEquals(ByteArray(0), aesGcmDecrypt(aesGcmEncrypt(ByteArray(0), key), key))
    }

    @Test
    fun aesGcm_usesAFreshIvPerCall() {
        val plaintext = "same payload twice".encodeToByteArray()
        val first = aesGcmEncrypt(plaintext, key)
        val second = aesGcmEncrypt(plaintext, key)
        // A reused IV under the same key breaks GCM outright, so this must never match.
        assertFalse(first.contentEquals(second))
        assertContentEquals(plaintext, aesGcmDecrypt(second, key))
    }

    @Test
    fun aesGcm_prependsThe12ByteIv() {
        // The layout is the wire format: iv || ciphertext || tag. Changing it silently
        // would leave every payload written before the change unreadable.
        val ciphertext = aesGcmEncrypt(ByteArray(0), key)
        assertEquals(12 + 16, ciphertext.size)
    }

    @Test
    fun aesGcm_refusesTheWrongKey() {
        val ciphertext = aesGcmEncrypt("secret".encodeToByteArray(), key)
        val otherKey = ByteArray(32) { (it + 1).toByte() }
        assertFailsWith<Exception> { aesGcmDecrypt(ciphertext, otherKey) }
    }

    @Test
    fun aesGcm_refusesATamperedPayload() {
        val ciphertext = aesGcmEncrypt("secret".encodeToByteArray(), key)
        ciphertext[ciphertext.size - 1] = (ciphertext[ciphertext.size - 1] + 1).toByte()
        assertFailsWith<Exception> { aesGcmDecrypt(ciphertext, key) }
    }

    @Test
    fun aesGcm_refusesAPayloadShorterThanTheIv() {
        assertFailsWith<IllegalArgumentException> { aesGcmDecrypt(ByteArray(8), key) }
    }

    @Test
    fun base64_roundTripsAndCarriesNoLineBreaks() {
        val bytes = ByteArray(200) { (it * 7).toByte() }
        val encoded = base64Encode(bytes)
        // android.util.Base64 wraps at 76 characters with its DEFAULT flags and is the
        // obvious wrong turn here; a wrapped string is not what the other side decodes.
        assertFalse(encoded.contains('\n'))
        assertFalse(encoded.contains('\r'))
        assertContentEquals(bytes, base64Decode(encoded))
    }

    @Test
    fun base64_matchesTheStandardAlphabet() {
        assertEquals("SGFsbG8=", base64Encode("Hallo".encodeToByteArray()))
        assertContentEquals("Hallo".encodeToByteArray(), base64Decode("SGFsbG8="))
    }

    @Test
    fun sha256_matchesTheKnownVector() {
        val expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        val actual = sha256("abc".encodeToByteArray())
            .joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        assertEquals(expected, actual)
    }

    @Test
    fun secureRandomBytes_hasTheAskedForLengthAndDoesNotRepeat() {
        assertEquals(0, secureRandomBytes(0).size)
        assertEquals(32, secureRandomBytes(32).size)
        assertNotEquals(
            secureRandomBytes(32).toList(),
            secureRandomBytes(32).toList(),
        )
        assertTrue(secureRandomBytes(64).any { it != 0.toByte() })
    }
}
