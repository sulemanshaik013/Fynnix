package com.fynnix.app.core.security

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PBKDF2-WithHmacSHA256, 200 000 iterations, 16-byte salt, 32-byte hash.
 *
 * Constant-time compare on verification to prevent timing oracles.
 */
object PinHasher {
    private const val ITERATIONS = 200_000
    private const val KEY_LENGTH_BITS = 256
    private const val ALGO = "PBKDF2WithHmacSHA256"

    fun newSalt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGO).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun verify(pin: String, salt: ByteArray, expected: ByteArray): Boolean {
        val candidate = hash(pin, salt)
        if (candidate.size != expected.size) return false
        var diff = 0
        for (i in candidate.indices) diff = diff or (candidate[i].toInt() xor expected[i].toInt())
        return diff == 0
    }
}
