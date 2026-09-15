package io.github.willywonka644.fintracker.sync

import io.github.willywonka644.fintracker.security.aesGcmDecrypt
import io.github.willywonka644.fintracker.security.aesGcmEncrypt
import io.github.willywonka644.fintracker.security.base64Decode
import io.github.willywonka644.fintracker.security.base64Encode
import io.github.willywonka644.fintracker.security.sha256

fun deriveKey(token: String): ByteArray = sha256(token.toByteArray(Charsets.UTF_8))

fun encryptSyncPayload(json: String, token: String): String {
    val key = deriveKey(token)
    val ciphertext = aesGcmEncrypt(json.toByteArray(Charsets.UTF_8), key)
    return base64Encode(ciphertext)
}

fun decryptSyncPayload(encoded: String, token: String): String {
    val key = deriveKey(token)
    val ciphertext = base64Decode(encoded)
    return aesGcmDecrypt(ciphertext, key).toString(Charsets.UTF_8)
}
