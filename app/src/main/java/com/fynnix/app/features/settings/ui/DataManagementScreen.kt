package com.fynnix.app.features.settings.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.RestorePage
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.fynnix.app.core.ui.theme.Primary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataManagementScreen(
    onBack: () -> Unit,
    onManageCategories: () -> Unit,
    onNavigateToFailedSms: () -> Unit = {},
) {
    val viewModel: DataManagementViewModel = hiltViewModel()
    val selectedDays by viewModel.selectedDays.collectAsState()
    val status by viewModel.status.collectAsState()
    val failedSmsCount by viewModel.failedSmsCount.collectAsState()
    val backupBusy by viewModel.backupBusy.collectAsState()
    val restoreWrongPassword by viewModel.restoreWrongPassword.collectAsState()
    val restoreCompleted by viewModel.restoreCompleted.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }

    var pendingBackupUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val context = LocalContext.current

    LaunchedEffect(status) {
        status?.let {
            pendingRestoreUri = null
            scope.launch {
                snackbarHostState.showSnackbar(it)
                viewModel.clearStatus()
            }
        }
    }

    LaunchedEffect(restoreCompleted) {
        if (restoreCompleted) pendingRestoreUri = null
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let(viewModel::exportCsv) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { pendingImportUri = it } }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> uri?.let { pendingBackupUri = it } }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { pendingRestoreUri = it } }

    val backupFileName = remember { "fynnix-backup-${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.db" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Data Management", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionHeader("INITIAL SCAN PERIOD")
            Text(
                text = "We'll re-scan your SMS inbox over this window. Larger windows take longer.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    viewModel.periodOptions.forEach { option ->
                        val selected = option.days == selectedDays
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectPeriod(option.days) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Schedule, contentDescription = null,
                                tint = if (selected) Primary else Color.Gray,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                option.label,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White,
                            )
                            if (selected) {
                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = Primary)
                            }
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                    }
                }
            }

            SectionHeader("EXPORT / IMPORT")

            ActionRow(
                icon = Icons.Default.CloudDownload,
                title = "Export to CSV",
                subtitle = "Save a copy of all transactions on this device",
                onClick = { exportLauncher.launch("fynnix-transactions.csv") },
            )
            ActionRow(
                icon = Icons.Default.CloudUpload,
                title = "Import from CSV",
                subtitle = "Re-import a previous export or another tool's CSV",
                onClick = { importLauncher.launch(arrayOf("text/*", "application/csv")) },
            )

            SectionHeader("BACKUP & RESTORE")
            Text(
                text = "A password-protected copy of everything — accounts, categories, budgets, " +
                    "transactions — that can be restored on any device, not just this one.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
            )
            ActionRow(
                icon = Icons.Default.Save,
                title = "Back up data",
                subtitle = "Save an encrypted copy you choose a password for",
                onClick = { backupLauncher.launch(backupFileName) },
            )
            ActionRow(
                icon = Icons.Default.RestorePage,
                title = "Restore from backup",
                subtitle = "Replace all data on this device with a backup file",
                onClick = { restoreLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
            )

            SectionHeader("OTHER SOURCES")
            ActionRow(
                icon = Icons.Default.AccountBalance,
                title = "Import bank statement",
                subtitle = "Under development — coming soon",
                onClick = {
                    scope.launch { snackbarHostState.showSnackbar("Bank statement import is under development") }
                },
                trailing = "Soon",
            )

            SectionHeader("DIAGNOSTICS")
            ActionRow(
                icon = Icons.Default.Warning,
                title = "Failed SMS Parsing",
                subtitle = "Review messages that couldn't be parsed",
                onClick = onNavigateToFailedSms,
                trailing = failedSmsCount.takeIf { it > 0 }?.let { if (it > 99) "99+" else "$it" },
                trailingIsAlert = failedSmsCount > 0,
            )

            SectionHeader("CATEGORIES")
            ActionRow(
                icon = Icons.Default.Category,
                title = "Manage categories & budgets",
                subtitle = "Add, edit or remove categories",
                onClick = onManageCategories,
            )
        }
    }

    if (pendingImportUri != null) {
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("Import from CSV?") },
            text = {
                Text(
                    "New transactions will be added to your history. Rows whose id " +
                        "already exists are skipped, not overwritten.",
                    color = Color.Gray,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.importCsv(pendingImportUri!!)
                    pendingImportUri = null
                }) { Text("Import", color = Primary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) { Text("Cancel") }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }

    if (pendingBackupUri != null) {
        BackupPasswordDialog(
            minLength = viewModel.backupPasswordMinLength,
            onDismiss = { pendingBackupUri = null },
            onConfirm = { password ->
                viewModel.backup(pendingBackupUri!!, password)
                pendingBackupUri = null
            },
        )
    }

    if (pendingRestoreUri != null) {
        RestorePasswordDialog(
            wrongPassword = restoreWrongPassword,
            onPasswordChanged = viewModel::clearRestoreWrongPassword,
            onDismiss = { pendingRestoreUri = null; viewModel.clearRestoreWrongPassword() },
            onConfirm = { password -> viewModel.restore(pendingRestoreUri!!, password) },
        )
    }

    if (backupBusy) {
        Dialog(onDismissRequest = {}) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(color = Primary, modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Working…", color = Color.White)
                }
            }
        }
    }

    if (restoreCompleted) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Restore complete") },
            text = { Text("Fynnix needs to restart to finish loading your restored data.", color = Color.Gray) },
            confirmButton = {
                TextButton(onClick = {
                    (context as? android.app.Activity)?.finishAffinity()
                    android.os.Process.killProcess(android.os.Process.myPid())
                }) { Text("Restart now", color = Primary, fontWeight = FontWeight.Bold) }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}

@Composable
private fun BackupPasswordDialog(
    minLength: Int,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val mismatch = confirm.isNotEmpty() && password != confirm
    val tooShort = password.isNotEmpty() && password.length < minLength
    val isValid = password.length >= minLength && password == confirm

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set a backup password") },
        text = {
            Column {
                Text(
                    "This password protects the backup file, not your app lock PIN. " +
                        "If you forget it, the backup cannot be recovered — Fynnix never stores it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = tooShort,
                    supportingText = { if (tooShort) Text("At least $minLength characters") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("Confirm password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = mismatch,
                    supportingText = { if (mismatch) Text("Doesn't match") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = isValid) {
                Text("Back Up", color = if (isValid) Primary else Color.Gray, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
private fun RestorePasswordDialog(
    wrongPassword: Boolean,
    onPasswordChanged: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore from backup") },
        text = {
            Column {
                Text(
                    "This replaces everything currently on this device with the backup's contents. " +
                        "This cannot be undone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; onPasswordChanged() },
                    label = { Text("Backup password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = wrongPassword,
                    supportingText = { if (wrongPassword) Text("Incorrect password for this file") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = password.isNotEmpty()) {
                Text("Restore", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
private fun SectionHeader(text: String) {
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = Color.Gray,
        letterSpacing = 1.sp,
    )
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: String? = null,
    trailingIsAlert: Boolean = false,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.clickable(onClick = onClick).padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = if (trailingIsAlert) Color(0xFFF59E0B) else Primary, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = Color.White)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            if (trailing != null) {
                val badgeColor = if (trailingIsAlert) Color(0xFFF59E0B) else Primary
                Surface(
                    color = badgeColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        trailing,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = badgeColor,
                    )
                }
            }
        }
    }
}
