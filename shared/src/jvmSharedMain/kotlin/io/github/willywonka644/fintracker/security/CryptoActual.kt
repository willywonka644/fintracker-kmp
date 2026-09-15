package io.github.willywonka644.fintracker.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

actual fun sha256(input: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(input)

actual fun secureRandomBytes(size: Int): ByteArray {
    val bytes = ByteArray(size)
    SecureRandom().nextBytes(bytes)
    return bytes
}

actual fun base64Encode(bytes: ByteArray): String =
    Base64.getEncoder().encodeToString(bytes)

actual fun base64Decode(encoded: String): ByteArray =
    Base64.getDecoder().decode(encoded)

actual fun aesGcmEncrypt(plaintext: ByteArray, key: ByteArray): ByteArray {
    val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
    val encrypted = cipher.doFinal(plaintext)
    return iv + encrypted
}

actual fun aesGcmDecrypt(ciphertext: ByteArray, key: ByteArray): ByteArray {
    require(ciphertext.size > 12) { "Ciphertext too short" }
    val iv = ciphertext.copyOfRange(0, 12)
    val encrypted = ciphertext.copyOfRange(12, ciphertext.size)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
    return cipher.doFinal(encrypted)
}
