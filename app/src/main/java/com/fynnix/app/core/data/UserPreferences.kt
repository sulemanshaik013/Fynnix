package com.fynnix.app.core.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores user preferences in [EncryptedSharedPreferences].
 *
 * Income and name are financial/personal data — plaintext SharedPreferences
 * is readable on rooted devices and via certain backup mechanisms. Using the
 * same AES-GCM master key scheme as [SecureKeyVault] keeps all sensitive prefs
 * under the Android Keystore umbrella.
 *
 * If the encrypted store cannot be opened (e.g. keystore wiped after factory
 * reset), we fall back to a plain SharedPreferences instance so the app stays
 * functional, logging the error for diagnostics.
 */
@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "UserPreferences"
        private const val PREFS_NAME = "fynnix_user_prefs_enc"
        private const val FALLBACK_PREFS_NAME = "fynnix_user_prefs"

        private const val KEY_USER_NAME           = "user_name"
        private const val KEY_USER_INCOME         = "user_income"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_SYNC_DAYS           = "sync_days"
        private const val KEY_LAST_SYNC_TS        = "last_sync_ts"
        private const val KEY_NOTIFICATIONS       = "notifications_enabled"
        private const val KEY_APP_LOCK            = "app_lock_enabled"
        private const val KEY_LOCK_METHOD         = "lock_method"
        private const val KEY_SCREENSHOT_BLOCK    = "screenshot_blocking_enabled"
        private const val KEY_LLM_MODEL_READY     = "llm_model_ready"
        private const val KEY_LLM_PARSER_FALLBACK = "llm_parser_fallback_enabled"
    }

    /** True once [openPrefs] has fallen back to plaintext storage this session. */
    var usingInsecureFallback: Boolean = false
        private set

    private val prefs: SharedPreferences by lazy { openPrefs() }

    private fun openPrefs(): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Cannot open encrypted prefs — falling back to plaintext", e)
            usingInsecureFallback = true
            context.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun saveUserName(name: String) { prefs.edit().putString(KEY_USER_NAME, name).apply() }
    fun getUserName(): String = prefs.getString(KEY_USER_NAME, "User") ?: "User"

    fun saveUserIncome(income: Double) { prefs.edit().putFloat(KEY_USER_INCOME, income.toFloat()).apply() }
    fun getUserIncome(): Double = prefs.getFloat(KEY_USER_INCOME, 0f).toDouble()

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }
    fun isOnboardingCompleted(): Boolean = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)

    fun getSyncDays(): Int = prefs.getInt(KEY_SYNC_DAYS, 90)
    fun setSyncDays(days: Int) { prefs.edit().putInt(KEY_SYNC_DAYS, days).apply() }

    fun getLastSyncTimestamp(): Long = prefs.getLong(KEY_LAST_SYNC_TS, 0L)
    fun setLastSyncTimestamp(ts: Long) { prefs.edit().putLong(KEY_LAST_SYNC_TS, ts).apply() }

    fun onboardingCompletedFlow(): Flow<Boolean> = callbackFlow {
        trySend(isOnboardingCompleted())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_ONBOARDING_COMPLETED) trySend(isOnboardingCompleted())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun isNotificationsEnabled(): Boolean = prefs.getBoolean(KEY_NOTIFICATIONS, true)
    fun setNotificationsEnabled(v: Boolean) { prefs.edit().putBoolean(KEY_NOTIFICATIONS, v).apply() }

    fun isAppLockEnabled(): Boolean = prefs.getBoolean(KEY_APP_LOCK, false)
    fun setAppLockEnabled(v: Boolean) { prefs.edit().putBoolean(KEY_APP_LOCK, v).apply() }

    fun getLockMethod(): String = prefs.getString(KEY_LOCK_METHOD, "biometric") ?: "biometric"
    fun setLockMethod(method: String) { prefs.edit().putString(KEY_LOCK_METHOD, method).apply() }

    /** Blocks screenshots and hides app content from the recent-apps thumbnail. On by
     *  default since this screen can show real transaction data; user-configurable
     *  because it also blocks the user's own screenshots (e.g. for support requests). */
    fun isScreenshotBlockingEnabled(): Boolean = prefs.getBoolean(KEY_SCREENSHOT_BLOCK, true)
    fun setScreenshotBlockingEnabled(v: Boolean) { prefs.edit().putBoolean(KEY_SCREENSHOT_BLOCK, v).apply() }

    fun screenshotBlockingEnabledFlow(): Flow<Boolean> = callbackFlow {
        trySend(isScreenshotBlockingEnabled())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_SCREENSHOT_BLOCK) trySend(isScreenshotBlockingEnabled())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    /** Set only after the model file is fully written and byte-count verified — never on
     *  download start, so an interrupted download can't be mistaken for a ready model. */
    fun isLlmModelReady(): Boolean = prefs.getBoolean(KEY_LLM_MODEL_READY, false)
    fun setLlmModelReady(v: Boolean) { prefs.edit().putBoolean(KEY_LLM_MODEL_READY, v).apply() }

    fun llmModelReadyFlow(): Flow<Boolean> = callbackFlow {
        trySend(isLlmModelReady())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_LLM_MODEL_READY) trySend(isLlmModelReady())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    /** Off by default even after the model is downloaded — unlike chat (user-initiated per
     *  message), this lets the LLM autonomously create transactions in the background, so it
     *  needs its own explicit opt-in rather than inheriting consent from the model download. */
    fun isLlmParserFallbackEnabled(): Boolean = prefs.getBoolean(KEY_LLM_PARSER_FALLBACK, false)
    fun setLlmParserFallbackEnabled(v: Boolean) { prefs.edit().putBoolean(KEY_LLM_PARSER_FALLBACK, v).apply() }

    fun llmParserFallbackEnabledFlow(): Flow<Boolean> = callbackFlow {
        trySend(isLlmParserFallbackEnabled())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_LLM_PARSER_FALLBACK) trySend(isLlmParserFallbackEnabled())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun clearAllData() { prefs.edit().clear().apply() }
}
