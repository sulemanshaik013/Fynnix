package com.fynnix.app.features.settings.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fynnix.app.core.llm.LlmModelState
import com.fynnix.app.core.llm.LlmModelViewModel
import com.fynnix.app.core.ui.theme.CardCornerRadius
import com.fynnix.app.core.ui.theme.Primary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LlmSettingsScreen(onBack: () -> Unit) {
    val viewModel: LlmModelViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val fallbackEnabled by viewModel.parserFallbackEnabled.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("On-device AI", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Everything here runs fully on your phone. No transaction ever leaves this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(CardCornerRadius),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(viewModel.spec.displayName, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                            Text(modelStatusLabel(state), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    if (state is LlmModelState.Downloading) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { (state as LlmModelState.Downloading).progressPercent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = Primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    when (state) {
                        is LlmModelState.Ready -> OutlinedButton(onClick = { showDeleteConfirm = true }) { Text("Delete model") }
                        is LlmModelState.Downloading -> OutlinedButton(onClick = { viewModel.cancelDownload() }) { Text("Cancel download") }
                        else -> Button(
                            onClick = { viewModel.startDownload() },
                            enabled = viewModel.isDeviceCapable,
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        ) { Text("Download", color = androidx.compose.ui.graphics.Color.Black) }
                    }
                    if (!viewModel.isDeviceCapable) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "This device doesn't have enough memory to run the on-device model.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(CardCornerRadius),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Catch missed transactions", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "When a bank SMS doesn't match any known format, let the on-device " +
                                "model try to extract it. Requires the model above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = fallbackEnabled,
                        onCheckedChange = { viewModel.setParserFallbackEnabled(it) },
                        enabled = state is LlmModelState.Ready,
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete downloaded model?") },
            text = { Text("You'll need to download it again to use the AI assistant or missed-transaction detection.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteModel(); showDeleteConfirm = false }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

private fun modelStatusLabel(state: LlmModelState): String = when (state) {
    is LlmModelState.Ready -> "Downloaded and ready"
    is LlmModelState.Downloading -> "Downloading — ${state.progressPercent}%"
    is LlmModelState.Failed -> state.message
    is LlmModelState.NotDownloaded -> "Not downloaded"
}
