package com.fynnix.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fynnix.app.ui.navigation.Screen
import com.fynnix.app.ui.navigation.bottomNavItems
import com.fynnix.app.ui.screens.*
import com.fynnix.app.ui.theme.FynnixTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FynnixTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Logic to show/hide bottom bar based on screen
    val onboardingRoutes = listOf(
        Screen.Welcome.route,
        Screen.PrivacyPermission.route,
        Screen.AboutYou.route,
        Screen.AllSet.route
    )
    val showBottomBar = currentDestination?.route !in onboardingRoutes

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp,
                    modifier = Modifier.height(80.dp)
                ) {
                    bottomNavItems.forEach { screen ->
                        val isSelected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                        NavigationBarItem(
                            icon = { 
                                Icon(
                                    imageVector = screen.icon, 
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                ) 
                            },
                            label = { 
                                Text(
                                    text = screen.title,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                ) 
                            },
                            selected = isSelected,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = Color.Gray,
                                unselectedTextColor = Color.Gray,
                                indicatorColor = Color.Transparent
                            ),
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Welcome.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            // Onboarding Flow
            composable(Screen.Welcome.route) {
                WelcomeScreen(onGetStartedClick = { navController.navigate(Screen.PrivacyPermission.route) })
            }
            composable(Screen.PrivacyPermission.route) {
                PrivacyPermissionScreen(
                    onBack = { navController.popBackStack() },
                    onAgreeAndContinue = { navController.navigate(Screen.AboutYou.route) },
                    onManualEntryClick = { navController.navigate(Screen.AboutYou.route) }
                )
            }
            composable(Screen.AboutYou.route) {
                AboutYouScreen(
                    onBack = { navController.popBackStack() },
                    onNext = { name, income, budgets -> 
                        navController.navigate(Screen.AllSet.route) 
                    }
                )
            }
            composable(Screen.AllSet.route) {
                AllSetScreen(onStartJourney = { 
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                })
            }

            // Main App Flow
            composable(Screen.Home.route) { 
                HomeScreen(
                    onSettingsClick = { navController.navigate(Screen.Settings.route) },
                    onSeeAllBudgetsClick = { navController.navigate(Screen.CategoriesAndBudgets.route) },
                    onViewAllTransactionsClick = { navController.navigate(Screen.Transactions.route) },
                    onViewAllInsightsClick = { navController.navigate(Screen.Insights.route) }
                ) 
            }
            composable(Screen.Transactions.route) { TransactionsScreen() }
            composable(Screen.Insights.route) { 
                InsightsScreen(
                    onEditAccountsClick = { navController.navigate(Screen.AccountManagement.route) }
                ) 
            }
            composable(Screen.Subscriptions.route) { SubscriptionsScreen() }
            
            // Settings Flow
            composable(Screen.Settings.route) { 
                SettingsScreen(
                    onNavigateToProfile = { navController.navigate(Screen.ProfileDetails.route) },
                    onNavigateToCategoriesAndBudgets = { navController.navigate(Screen.CategoriesAndBudgets.route) },
                    onNavigateToPrivacy = { navController.navigate(Screen.PrivacyDashboard.route) },
                    onNavigateToSecurity = { navController.navigate(Screen.SecuritySettings.route) },
                    onNavigateToBackup = { navController.navigate(Screen.BackupRestore.route) }
                ) 
            }
            
            composable(Screen.ProfileDetails.route) {
                ProfileDetailsScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.CategoriesAndBudgets.route) {
                CategoriesAndBudgetsScreen(onBack = { navController.popBackStack() })
            }
            
            composable(Screen.BackupRestore.route) {
                BackupRestoreScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.PrivacyDashboard.route) {
                PrivacyDashboardScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.SecuritySettings.route) {
                SecuritySettingsScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.AccountManagement.route) {
                Text("Account Management Screen", modifier = Modifier.padding(16.dp))
            }
        }
    }
}