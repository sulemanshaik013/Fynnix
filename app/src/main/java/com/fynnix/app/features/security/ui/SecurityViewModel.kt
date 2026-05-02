package com.fynnix.app.features.security.ui

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.lifecycle.ViewModel
import com.fynnix.app.core.data.UserPreferences
import com.fynnix.app.core.security.PinHasher
import com.fynnix.app.core.security.SecureKeyVault
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.pow

enum class LockMethod(val key: String) { Biometric("biometric"), Pin("pin"), Device("device") }

sealed interface PinVerifyResult {
    data object Success : PinVerifyResult
    data class WrongPin(val attemptsRemaining: Int) : PinVerifyResult
    data class LockedOut(val remainingMs: Long) : PinVerifyResult
}

data class SecurityUiState(
    val appLockEnabled: Boolean,
    val method: LockMethod,
    val biometricAvailable: Boolean,
    val pinSet: Boolean,
)

@HiltViewModel
class SecurityViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: UserPreferences,
    private val vault: SecureKeyVault,
) : ViewModel() {

    private val _state = MutableStateFlow(currentState())
    val state: StateFlow<SecurityUiState> = _state

    fun setAppLockEnabled(v: Boolean) {
        prefs.setAppLockEnabled(v)
        if (!v) {
            // Disabling lock wipes any stored PIN.
            vault.clearPin()
        }
        _state.value = currentState()
    }

    fun setMethod(method: LockMethod) {
        prefs.setLockMethod(method.key)
        _state.value = currentState()
    }

    /** Stores a PBKDF2 hash of [pin] with a fresh salt. Returns true on success. */
    fun savePin(pin: String): Boolean {
        if (pin.length < 4 || pin.length > 6 || !pin.all(Char::isDigit)) return false
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash(pin, salt)
        vault.setPinSalt(salt)
        vault.setPinHash(hash)
        _state.value = currentState()
        return true
    }

    /**
     * Verifies [pin] against the stored hash, applying an exponentially growing lockout
     * after [MAX_FREE_ATTEMPTS] consecutive failures. A 4-6 digit PIN has too little
     * entropy to survive unlimited guessing, so repeated wrong attempts must cost
     * increasing wall-clock time rather than only a PBKDF2 round-trip. Runs off the
     * main thread since PBKDF2 at this iteration count is too slow to do inline with
     * UI input.
     */
    suspend fun verifyPin(pin: String): PinVerifyResult = withContext(Dispatchers.Default) {
        val lockoutUntil = vault.getLockoutUntil()
        val now = System.currentTimeMillis()
        if (now < lockoutUntil) return@withContext PinVerifyResult.LockedOut(lockoutUntil - now)

        val salt = vault.getPinSalt()
        val expected = vault.getPinHash()
        val matches = salt != null && expected != null && PinHasher.verify(pin, salt, expected)

        if (matches) {
            vault.resetPinAttempts()
            PinVerifyResult.Success
        } else {
            val attempts = vault.recordFailedPinAttempt()
            if (attempts >= MAX_FREE_ATTEMPTS) {
                val lockoutMs = backoffMillis(attempts)
                vault.setLockoutUntil(System.currentTimeMillis() + lockoutMs)
                PinVerifyResult.LockedOut(lockoutMs)
            } else {
                PinVerifyResult.WrongPin(MAX_FREE_ATTEMPTS - attempts)
            }
        }
    }

    /** Doubles per attempt past the free allowance, capped so the app never locks out permanently. */
    private fun backoffMillis(attempts: Int): Long {
        val overage = attempts - MAX_FREE_ATTEMPTS
        val scaled = BASE_LOCKOUT_MS * 2.0.pow(overage)
        return min(scaled, MAX_LOCKOUT_MS.toDouble()).toLong()
    }

    fun clearPin() {
        vault.clearPin()
        _state.value = currentState()
    }

    private fun currentState(): SecurityUiState {
        val biometricAvailable = BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
        return SecurityUiState(
            appLockEnabled = prefs.isAppLockEnabled(),
            method = LockMethod.values().firstOrNull { it.key == prefs.getLockMethod() } ?: LockMethod.Biometric,
            biometricAvailable = biometricAvailable,
            pinSet = vault.getPinHash() != null,
        )
    }

    private companion object {
        const val MAX_FREE_ATTEMPTS = 5
        const val BASE_LOCKOUT_MS = 5_000L
        const val MAX_LOCKOUT_MS = 5 * 60_000L
    }
}
