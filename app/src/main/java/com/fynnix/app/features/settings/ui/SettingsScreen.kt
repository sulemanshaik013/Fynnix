package com.fynnix.app.features.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fynnix.app.core.ui.theme.Primary

@Composable
fun SettingsScreen(
    onNavigateToProfile: () -> Unit,
    onNavigateToDataManagement: () -> Unit,
    onNavigateToAppSettings: () -> Unit,
    onNavigateToSecurity: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToHelp: () -> Unit,
    onNavigateToReport: () -> Unit,
    onNavigateToLlmSettings: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val failedCount by viewModel.failedSmsCount.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 12.dp),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        ) {
            item { SectionHeader("PROFILE") }
            item {
                SettingsRow(
                    icon     = Icons.Default.AccountCircle,
                    title    = "Profile",
                    subtitle = "Name, monthly income, budgets",
                    onClick  = onNavigateToProfile,
                )
            }

            item { SectionHeader("DATA MANAGEMENT") }
            item {
                SettingsRow(
                    icon     = Icons.Default.Storage,
                    title    = "Data Management",
                    subtitle = "Scan period, export/import, categories",
                    badge    = failedCount.takeIf { it > 0 },
                    onClick  = onNavigateToDataManagement,
                )
            }

            item { SectionHeader("APP") }
            item {
                SettingsRow(
                    icon     = Icons.Default.Settings,
                    title    = "App Settings",
                    subtitle = "Notifications & preferences",
                    onClick  = onNavigateToAppSettings,
                )
            }

            item { SectionHeader("SECURITY") }
            item {
                SettingsRow(
                    icon     = Icons.Default.Lock,
                    title    = "Security",
                    subtitle = "App lock with biometric, PIN or device lock",
                    onClick  = onNavigateToSecurity,
                )
            }

            item { SectionHeader("AI") }
            item {
                SettingsRow(
                    icon     = Icons.Default.AutoAwesome,
                    title    = "On-device AI",
                    subtitle = "Chat assistant & missed-transaction detection",
                    onClick  = onNavigateToLlmSettings,
                )
            }

            item { SectionHeader("PRIVACY & SUPPORT") }
            item {
                SettingsRow(
                    icon     = Icons.Default.PrivacyTip,
                    title    = "Privacy Dashboard",
                    subtitle = "How your data is used",
                    onClick  = onNavigateToPrivacy,
                )
            }
            item {
                SettingsRow(
                    icon     = Icons.AutoMirrored.Filled.HelpOutline,
                    title    = "Help",
                    subtitle = "FAQs and tips",
                    onClick  = onNavigateToHelp,
                )
            }
            item {
                SettingsRow(
                    icon     = Icons.Default.BugReport,
                    title    = "Report an Issue",
                    subtitle = "Send feedback via email",
                    onClick  = onNavigateToReport,
                )
            }

            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text          = text,
        style         = MaterialTheme.typography.labelSmall,
        color         = Color.Gray,
        letterSpacing = 1.sp,
        modifier      = Modifier.padding(vertical = 6.dp),
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    badge: Int? = null,
) {
    Card(
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape    = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(
            modifier          = Modifier.clickable(onClick = onClick).padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = Color.White)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            if (badge != null && badge > 0) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier
                        .size(22.dp)
                        .background(MaterialTheme.colorScheme.error, CircleShape),
                ) {
                    Text(
                        text  = if (badge > 99) "99+" else badge.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = Color.White,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
        }
    }
}
