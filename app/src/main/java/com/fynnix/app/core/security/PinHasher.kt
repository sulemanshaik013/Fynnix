package com.fynnix.app.core.security

import com.fynnix.app.core.utils.AppConstants
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PBKDF2-WithHmacSHA256, 200 000 iterations, 16-byte salt, 32-byte hash.
 *
 * Constant-time compare on verification to prevent timing oracles.
 */
object PinHasher {
    private const val ALGO = "PBKDF2WithHmacSHA256"

    fun newSalt(): ByteArray = ByteArray(AppConstants.PIN_SALT_BYTES).also { SecureRandom().nextBytes(it) }

    fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, AppConstants.PIN_PBKDF2_ITERATIONS, AppConstants.PIN_HASH_BITS)
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
