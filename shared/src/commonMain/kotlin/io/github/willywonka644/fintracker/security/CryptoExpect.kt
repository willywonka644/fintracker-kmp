package io.github.willywonka644.fintracker.security

expect fun sha256(input: ByteArray): ByteArray
expect fun secureRandomBytes(size: Int): ByteArray
expect fun base64Encode(bytes: ByteArray): String
expect fun base64Decode(encoded: String): ByteArray
expect fun aesGcmEncrypt(plaintext: ByteArray, key: ByteArray): ByteArray
expect fun aesGcmDecrypt(ciphertext: ByteArray, key: ByteArray): ByteArray
