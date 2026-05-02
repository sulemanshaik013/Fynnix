package com.fynnix.app.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hardware-backed key vault.
 *
 * - Holds the SQLCipher passphrase used to encrypt the Room database.
 * - Holds the PBKDF2 salt + iteration count + hash for the user's PIN.
 *
 * The underlying [MasterKey] is generated via Android Keystore using AES-GCM.
 * Without the user's device unlock + the keystore master key, none of these
 * values are recoverable from a backup or rooted dump.
 */
@Singleton
class SecureKeyVault @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences by lazy {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** Generates the DB passphrase on first call, returns the same bytes after. */
    fun getOrCreateDbPassphrase(): ByteArray {
        prefs.getString(KEY_DB_PASSPHRASE, null)?.let { return decode(it) }
        val random = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(KEY_DB_PASSPHRASE, encode(random)).apply()
        return random
    }

    fun getPinSalt(): ByteArray? = prefs.getString(KEY_PIN_SALT, null)?.let(::decode)

    fun setPinSalt(salt: ByteArray) {
        prefs.edit().putString(KEY_PIN_SALT, encode(salt)).apply()
    }

    fun getPinHash(): ByteArray? = prefs.getString(KEY_PIN_HASH, null)?.let(::decode)

    fun setPinHash(hash: ByteArray?) {
        prefs.edit().run {
            if (hash == null) remove(KEY_PIN_HASH) else putString(KEY_PIN_HASH, encode(hash))
            apply()
        }
    }

    fun clearPin() {
        prefs.edit()
            .remove(KEY_PIN_HASH).remove(KEY_PIN_SALT)
            .remove(KEY_PIN_ATTEMPTS).remove(KEY_PIN_LOCKOUT_UNTIL)
            .apply()
    }

    /** Increments the failed-PIN-attempt counter and returns the new count. */
    fun recordFailedPinAttempt(): Int {
        val next = prefs.getInt(KEY_PIN_ATTEMPTS, 0) + 1
        prefs.edit().putInt(KEY_PIN_ATTEMPTS, next).apply()
        return next
    }

    fun resetPinAttempts() {
        prefs.edit().remove(KEY_PIN_ATTEMPTS).remove(KEY_PIN_LOCKOUT_UNTIL).apply()
    }

    fun getLockoutUntil(): Long = prefs.getLong(KEY_PIN_LOCKOUT_UNTIL, 0L)

    fun setLockoutUntil(epochMs: Long) {
        prefs.edit().putLong(KEY_PIN_LOCKOUT_UNTIL, epochMs).apply()
    }

    private fun encode(b: ByteArray): String =
        android.util.Base64.encodeToString(b, android.util.Base64.NO_WRAP)

    private fun decode(s: String): ByteArray =
        android.util.Base64.decode(s, android.util.Base64.NO_WRAP)

    private companion object {
        const val FILE_NAME = "fynnix_secure"
        const val KEY_DB_PASSPHRASE = "db_passphrase"
        const val KEY_PIN_SALT = "pin_salt"
        const val KEY_PIN_HASH = "pin_hash"
        const val KEY_PIN_ATTEMPTS = "pin_attempts"
        const val KEY_PIN_LOCKOUT_UNTIL = "pin_lockout_until"
    }
}
