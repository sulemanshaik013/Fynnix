package com.fynnix.app.features.security.ui

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.lifecycle.ViewModel
import com.fynnix.app.core.data.UserPreferences
import com.fynnix.app.core.security.PinHasher
import com.fynnix.app.core.security.SecureKeyVault
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

enum class LockMethod(val key: String) { Biometric("biometric"), Pin("pin"), Device("device") }

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

    fun verifyPin(pin: String): Boolean {
        val salt = vault.getPinSalt() ?: return false
        val expected = vault.getPinHash() ?: return false
        return PinHasher.verify(pin, salt, expected)
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
}
