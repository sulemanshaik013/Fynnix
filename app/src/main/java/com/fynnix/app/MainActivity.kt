package com.fynnix.app

import android.Manifest
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.fynnix.app.core.data.UserPreferences
import com.fynnix.app.core.notifications.BillReminderWorker
import com.fynnix.app.core.sms.SmsProcessingManager
import com.fynnix.app.core.sms.SyncWorker
import com.fynnix.app.core.ui.theme.FynnixTheme
import com.fynnix.app.features.accounts.ui.AccountManagementScreen
import com.fynnix.app.features.assistant.ui.AssistantScreen
import com.fynnix.app.features.categories.ui.CategoriesAndBudgetsScreen
import com.fynnix.app.features.home.ui.HomeScreen
import com.fynnix.app.features.insights.ui.InsightsScreen
import com.fynnix.app.features.onboarding.ui.AboutYouScreen
import com.fynnix.app.features.onboarding.ui.AllSetScreen
import com.fynnix.app.features.onboarding.ui.WelcomeScreen
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.fynnix.app.features.security.ui.AppLockScreen
import com.fynnix.app.features.security.ui.SecuritySettingsScreen
import com.fynnix.app.features.settings.ui.AppSettingsScreen
import com.fynnix.app.features.settings.ui.DataManagementScreen
import com.fynnix.app.features.settings.ui.FailedSmsReviewScreen
import com.fynnix.app.features.settings.ui.HelpScreen
import com.fynnix.app.features.settings.ui.LlmSettingsScreen
import com.fynnix.app.features.settings.ui.PrivacyDashboardScreen
import com.fynnix.app.features.settings.ui.ProfileDetailsScreen
import com.fynnix.app.features.settings.ui.ReportIssueScreen
import com.fynnix.app.features.settings.ui.SettingsScreen
import com.fynnix.app.features.subscriptions.ui.SubscriptionsScreen
import com.fynnix.app.features.transactions.ui.TransactionsScreen
import com.fynnix.app.navigation.Screen
import com.fynnix.app.navigation.bottomNavItems
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    
    @javax.inject.Inject
    lateinit var userPrefs: UserPreferences

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Bill notifications simply stay silent if this is denied — nothing else to do. */ }

    private val smsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            SmsProcessingManager.startProcessing()
            SyncWorker.scheduleInitialSync(this, userPrefs.getSyncDays())
            SyncWorker.schedulePeriodicSync(this)
            BillReminderWorker.Scheduler.schedulePeriodic(this)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            // Permission denied — onboarding can still proceed (manual mode).
            android.util.Log.w("MainActivity", "READ_SMS denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FynnixTheme {
                // Blocks screenshots and hides app content from the recent-apps thumbnail
                // — user-toggleable in Settings, so applied reactively rather than once,
                // fixed, in onCreate.
                LaunchedEffect(Unit) {
                    userPrefs.screenshotBlockingEnabledFlow().collect { enabled ->
                        if (enabled) {
                            window.setFlags(
                                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                            )
                        } else {
                            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                        }
                    }
                }
                AppRoot(
                    smsPermissionLauncher = smsPermissionLauncher,
                    userPreferences = userPrefs,
                )
            }
        }
    }
}

/**
 * Holds unlock state in a ViewModel rather than `remember` so it survives a
 * configuration change (rotation) instead of forcing the user to re-authenticate.
 * A ViewModel is still discarded on real process death, so a fully killed-and-
 * recreated process correctly starts locked again — only in-process recreation
 * (rotation) should skip re-prompting.
 */
@HiltViewModel
class AppLockStateViewModel @Inject constructor(
    userPreferences: UserPreferences,
) : ViewModel() {
    var unlocked by mutableStateOf(!userPreferences.isAppLockEnabled())
    var lastStop by mutableStateOf(0L)
}

@Composable
fun AppRoot(
    smsPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    userPreferences: UserPreferences,
) {
    val lockState: AppLockStateViewModel = hiltViewModel()

    // Re-lock when the activity goes to background and comes back > BACKGROUND_RELOCK_MS later.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> lockState.lastStop = System.currentTimeMillis()
                Lifecycle.Event.ON_START -> {
                    if (userPreferences.isAppLockEnabled() &&
                        lockState.lastStop > 0 &&
                        System.currentTimeMillis() - lockState.lastStop > BACKGROUND_RELOCK_MS
                    ) {
                        lockState.unlocked = false
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!lockState.unlocked) {
        AppLockScreen(onUnlocked = { lockState.unlocked = true })
    } else {
        MainScreen(
            smsPermissionLauncher = smsPermissionLauncher,
            userPreferences = userPreferences,
        )
    }
}

private const val BACKGROUND_RELOCK_MS = 30_000L

@Composable
fun MainScreen(
    smsPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    userPreferences: UserPreferences
) {
    val navController = rememberNavController()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val isOnboardingCompleted by userPreferences
        .onboardingCompletedFlow()
        .collectAsState(initial = userPreferences.isOnboardingCompleted())

    val startDestination = if (isOnboardingCompleted) Screen.Home.route else Screen.Welcome.route
    // Logic to show/hide bottom bar based on screen
    val onboardingRoutes = listOf(
        Screen.Welcome.route,
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
        val tweenSpec = tween<Float>(durationMillis = 220)
        val slideMs = 240
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(slideMs),
                ) + fadeIn(tweenSpec)
            },
            exitTransition = { fadeOut(tweenSpec) },
            popEnterTransition = { fadeIn(tweenSpec) },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(slideMs),
                ) + fadeOut(tweenSpec)
            },
        ) {
            // Onboarding Flow
            composable(Screen.Welcome.route) {
                WelcomeScreen(
                    onAgreeAndContinue = {
                        smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
                        navController.navigate(Screen.AboutYou.route)
                    },
                    onManualEntryClick = { navController.navigate(Screen.AboutYou.route) }
                )
            }
            composable(Screen.AboutYou.route) {
                AboutYouScreen(
                    onBack = { navController.popBackStack() },
                    onNext = { navController.navigate(Screen.AllSet.route) }
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
//                    onSettingsClick = { navController.navigate(Screen.Settings.route) },
                    onSeeAllBudgetsClick = { navController.navigate(Screen.CategoriesAndBudgets.route) },
                    onViewAllTransactionsClick = { navController.navigate(Screen.Transactions.route) },
                    onViewAllInsightsClick = { navController.navigate(Screen.Insights.route) },
                    onAiChatClick = { navController.navigate(Screen.Assistant.route) },
                )
            }
            composable(Screen.Assistant.route) {
                AssistantScreen(onBack = { navController.popBackStack() })
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
                    onNavigateToDataManagement = { navController.navigate(Screen.DataManagement.route) },
                    onNavigateToAppSettings = { navController.navigate(Screen.AppSettings.route) },
                    onNavigateToSecurity = { navController.navigate(Screen.SecuritySettings.route) },
                    onNavigateToPrivacy = { navController.navigate(Screen.PrivacyDashboard.route) },
                    onNavigateToHelp = { navController.navigate(Screen.Help.route) },
                    onNavigateToReport = { navController.navigate(Screen.ReportIssue.route) },
                    onNavigateToLlmSettings = { navController.navigate(Screen.LlmSettings.route) },
                )
            }

            composable(Screen.LlmSettings.route) {
                LlmSettingsScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.ProfileDetails.route) {
                ProfileDetailsScreen(
                    onBack = { navController.popBackStack() },
                    onEditBudgets = { navController.navigate(Screen.CategoriesAndBudgets.route) },
                )
            }

            composable(Screen.CategoriesAndBudgets.route) {
                CategoriesAndBudgetsScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.DataManagement.route) {
                DataManagementScreen(
                    onBack = { navController.popBackStack() },
                    onManageCategories = { navController.navigate(Screen.CategoriesAndBudgets.route) },
                    onNavigateToFailedSms = { navController.navigate(Screen.FailedSmsReview.route) },
                )
            }

            composable(Screen.FailedSmsReview.route) {
                FailedSmsReviewScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.AppSettings.route) {
                AppSettingsScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.SecuritySettings.route) {
                SecuritySettingsScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.PrivacyDashboard.route) {
                PrivacyDashboardScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.Help.route) {
                HelpScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.ReportIssue.route) {
                ReportIssueScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.AccountManagement.route) {
                AccountManagementScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}