package com.fynnix.app.features.security.ui

import androidx.activity.compose.LocalActivity
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.fynnix.app.core.ui.theme.Primary
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Blocks the rest of the UI tree until the user authenticates with the chosen
 * lock method (biometric / PIN / device credential).
 */
@Composable
fun AppLockScreen(onUnlocked: () -> Unit) {
    val viewModel: SecurityViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }
    val activity = LocalActivity.current as? FragmentActivity

    LaunchedEffect(Unit) {
        if (state.method == LockMethod.Biometric || state.method == LockMethod.Device) {
            activity?.let { promptBiometric(it, state.method, onSuccess = onUnlocked) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Primary.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(40.dp),
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Fynnix is locked",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(32.dp))

            when (state.method) {
                LockMethod.Pin -> {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            if (it.length <= 6 && it.all(Char::isDigit)) {
                                pinInput = it
                                pinError = false
                                if (it.length >= 4 && viewModel.verifyPin(it)) {
                                    onUnlocked()
                                }
                            }
                        },
                        label = { Text("PIN") },
                        singleLine = true,
                        isError = pinError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (!viewModel.verifyPin(pinInput)) {
                                pinError = true; pinInput = ""
                            } else onUnlocked()
                        },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    ) { Text("Unlock", color = Color.Black, fontWeight = FontWeight.Bold) }
                    if (pinError) Text("Wrong PIN", color = MaterialTheme.colorScheme.error)
                }
                LockMethod.Biometric, LockMethod.Device -> {
                    Button(
                        onClick = {
                            activity?.let { promptBiometric(it, state.method, onSuccess = onUnlocked) }
                        },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (state.method == LockMethod.Device) "Use device unlock" else "Use biometric",
                            color = Color.Black, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

private fun promptBiometric(
    activity: FragmentActivity,
    method: LockMethod,
    onSuccess: () -> Unit,
) {
    val executor: Executor = Executors.newSingleThreadExecutor()
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            activity.runOnUiThread { onSuccess() }
        }
    }
    val prompt = BiometricPrompt(activity, executor, callback)

    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock Fynnix")
        .setSubtitle("Authenticate to view your finances")
        .apply {
            if (method == LockMethod.Device) {
                setAllowedAuthenticators(
                    androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL or
                        androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
                )
            } else {
                setNegativeButtonText("Cancel")
                setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK)
            }
        }
        .build()

    runCatching { prompt.authenticate(info) }
}
