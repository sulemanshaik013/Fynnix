package com.fynnix.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fynnix.app.ui.theme.Primary

@Composable
fun SettingsScreen(
    onNavigateToProfile: () -> Unit,
    onNavigateToCategoriesAndBudgets: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToSecurity: () -> Unit,
    onNavigateToBackup: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp
            ),
            color = Color.White,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 40.dp)
        ) {
            item {
                SettingsGroup(title = "PROFILE") {
                    SettingsItem(
                        icon = Icons.Default.Person,
                        title = "Profile Details",
                        subtitle = "Name, Monthly Income",
                        onClick = onNavigateToProfile
                    )
                    SettingsItem(
                        icon = Icons.Default.Category,
                        title = "Categories & Budgets",
                        subtitle = "Manage spending limits and icons",
                        onClick = onNavigateToCategoriesAndBudgets
                    )
                }
            }

            item {
                SettingsGroup(title = "APP SETTINGS") {
                    SettingsItem(
                        icon = Icons.Default.Lock,
                        title = "Security",
                        subtitle = "PIN, Biometrics, App lock",
                        onClick = onNavigateToSecurity
                    )
                }
            }

            item {
                SettingsGroup(title = "DATA & PRIVACY") {
                    SettingsItem(
                        icon = Icons.Default.PrivacyTip,
                        title = "Privacy Dashboard",
                        subtitle = "Manage data, permissions and consent",
                        onClick = onNavigateToPrivacy
                    )
                    SettingsItem(
                        icon = Icons.Default.Backup,
                        title = "Backup & Restore",
                        subtitle = "Protect your data locally",
                        onClick = onNavigateToBackup
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Fynnix v1.0.0",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                    Text(
                        text = "Privacy First • Local Only",
                        style = MaterialTheme.typography.labelSmall,
                        color = Primary.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(top = 24.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = Color.Gray,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        Column(content = content)
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    ),
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.Gray.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
