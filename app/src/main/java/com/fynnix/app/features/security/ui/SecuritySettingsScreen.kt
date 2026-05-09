package com.fynnix.app.features.security.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fynnix.app.core.ui.theme.Primary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsScreen(onBack: () -> Unit) {
    val viewModel: SecurityViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    var showPinDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("App Lock", style = MaterialTheme.typography.bodyLarge, color = Color.White)
                        Text(
                            "Require authentication when opening Fynnix",
                            style = MaterialTheme.typography.bodySmall, color = Color.Gray,
                        )
                    }
                    Switch(
                        checked = state.appLockEnabled,
                        onCheckedChange = { viewModel.setAppLockEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Primary,
                        ),
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Screenshot, contentDescription = null, tint = Primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Block Screenshots", style = MaterialTheme.typography.bodyLarge, color = Color.White)
                        Text(
                            "Hide app content from screenshots and the recent-apps preview",
                            style = MaterialTheme.typography.bodySmall, color = Color.Gray,
                        )
                    }
                    Switch(
                        checked = state.screenshotBlockingEnabled,
                        onCheckedChange = { viewModel.setScreenshotBlockingEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Primary,
                        ),
                    )
                }
            }

            if (state.appLockEnabled) {
                Text(
                    text = "LOCK METHOD",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    letterSpacing = 1.sp,
                )

                MethodRow(
                    icon = Icons.Default.Fingerprint,
                    title = "Biometric",
                    subtitle = if (state.biometricAvailable)
                        "Fingerprint or face unlock" else "Not available on this device",
                    selected = state.method == LockMethod.Biometric,
                    enabled = state.biometricAvailable,
                    onClick = { viewModel.setMethod(LockMethod.Biometric) },
                )
                MethodRow(
                    icon = Icons.Default.Pin,
                    title = "PIN",
                    subtitle = if (state.pinSet) "PIN configured" else "Set a 4–6 digit PIN",
                    selected = state.method == LockMethod.Pin,
                    enabled = true,
                    trailing = if (state.method == LockMethod.Pin) {
                        { TextButton(onClick = { showPinDialog = true }) { Text(if (state.pinSet) "Change" else "Set") } }
                    } else null,
                    onClick = {
                        viewModel.setMethod(LockMethod.Pin)
                        if (!state.pinSet) showPinDialog = true
                    },
                )
                MethodRow(
                    icon = Icons.Default.Smartphone,
                    title = "Device lock",
                    subtitle = "Use the device's screen lock",
                    selected = state.method == LockMethod.Device,
                    enabled = true,
                    onClick = { viewModel.setMethod(LockMethod.Device) },
                )
            }
        }
    }

    if (showPinDialog) {
        PinSetupDialog(
            onDismiss = { showPinDialog = false },
            onSave = { pin ->
                if (viewModel.savePin(pin)) showPinDialog = false
            },
        )
    }
}

@Composable
private fun MethodRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.clickable(enabled = enabled, onClick = onClick).padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = if (enabled) Primary else Color.Gray)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) Color.White else Color.Gray,
                )
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            trailing?.invoke()
            if (selected && trailing == null) {
                Text("Selected", color = Primary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinSetupDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val mismatch = confirm.isNotEmpty() && pin != confirm
    val tooShort = pin.isNotEmpty() && pin.length < 4

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
                    label = { Text("PIN (4–6 digits)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    isError = tooShort,
                    singleLine = true,
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) confirm = it },
                    label = { Text("Confirm PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    isError = mismatch,
                    singleLine = true,
                )
                if (mismatch) Text("PINs don't match", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(pin) },
                enabled = pin.length in 4..6 && pin == confirm,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
