package com.fynnix.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    // Onboarding
    object Welcome : Screen("welcome", "Welcome", Icons.Default.Home)
    object PrivacyPermission : Screen("privacy_permission", "Privacy", Icons.Default.Home)
    object AboutYou : Screen("about_you", "About You", Icons.Default.Home)
    object AllSet : Screen("all_set", "All Set", Icons.Default.Home)

    // Main
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Transactions : Screen("transactions", "Transactions", Icons.AutoMirrored.Filled.List)
    object Insights : Screen("insights", "Insights", Icons.Default.BarChart)
    object Subscriptions : Screen("subscriptions", "Subscriptions", Icons.Default.Subscriptions)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    
    // Settings Sub-screens
    object ProfileDetails : Screen("profile_details", "Profile Details", Icons.Default.Settings)
    object CategoriesAndBudgets : Screen("categories_and_budgets", "Categories & Budgets", Icons.Default.Settings)
    object PrivacyDashboard : Screen("privacy_dashboard", "Privacy", Icons.Default.Settings)
    object BackupRestore : Screen("backup_restore", "Backup & Restore", Icons.Default.Settings)
    object AccountManagement : Screen("account_management", "Manage Accounts", Icons.Default.Settings)
    object SecuritySettings : Screen("security_settings", "Security", Icons.Default.Settings)
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Transactions,
    Screen.Insights,
    Screen.Subscriptions,
    Screen.Settings
)
