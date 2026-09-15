package io.github.willywonka644.fintracker.security

object PinHasher {
    private const val SALT_BYTES = 16

    fun generateSalt(): String {
        val salt = secureRandomBytes(SALT_BYTES)
        return base64Encode(salt)
    }

    fun hashPin(pin: String, saltBase64: String): String {
        val salt = base64Decode(saltBase64)
        val hash = sha256(salt + pin.encodeToByteArray())
        return base64Encode(hash)
    }
}
