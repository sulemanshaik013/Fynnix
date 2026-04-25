package com.fynnix.app.features.home.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.fynnix.app.features.budgets.ui.components.BudgetSection
import com.fynnix.app.features.home.ui.components.HomeTopBar
import com.fynnix.app.features.home.ui.components.RecentTransactionsSection
import com.fynnix.app.features.home.ui.components.SpendCardPager
import com.fynnix.app.features.home.ui.components.StreakBanner
import com.fynnix.app.features.insights.ui.InsightUiModel
import com.fynnix.app.features.insights.ui.components.InsightDetailSheet
import com.fynnix.app.features.insights.ui.components.InsightsSection

@Composable
fun HomeScreen(
    onSeeAllBudgetsClick: () -> Unit,
    onViewAllTransactionsClick: () -> Unit,
    onViewAllInsightsClick: () -> Unit,
) {
    val viewModel: HomeViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val syncProgress by viewModel.syncProgress.collectAsState()

    var selectedInsight by remember { mutableStateOf<InsightUiModel?>(null) }
    var showInsightSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        HomeTopBar(
            name             = viewModel.userName,
            onRefreshClick   = { viewModel.manualSync() },
            onNotificationsClick = { },
            onAiChatClick    = { }
        )

        // SMS permission revocation banner
        val context    = LocalContext.current
        val smsGranted = remember(context) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
                PackageManager.PERMISSION_GRANTED
        }
        if (!smsGranted) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape  = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.15f)),
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier          = Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "SMS permission is off. Auto-tracking is paused.\nGrant access in System Settings → Apps → Fynnix.",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        if (syncProgress.isProcessing) {
            LinearProgressIndicator(
                progress     = { syncProgress.progress },
                modifier     = Modifier.fillMaxWidth(),
                color        = MaterialTheme.colorScheme.primary,
                trackColor   = MaterialTheme.colorScheme.surfaceVariant,
            )
            Text(
                text     = syncProgress.status,
                style    = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color    = MaterialTheme.colorScheme.primary,
            )
        }

        if (uiState.streak > 0) StreakBanner(streakCount = uiState.streak)

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.spendCards.isNotEmpty()) {
            SpendCardPager(cards = uiState.spendCards)
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (uiState.budgets.isNotEmpty()) {
            BudgetSection(budgets = uiState.budgets, onSeeAllClick = onSeeAllBudgetsClick)
            Spacer(modifier = Modifier.height(24.dp))
        }

        RecentTransactionsSection(
            transactions  = uiState.transactions,
            onViewAllClick = onViewAllTransactionsClick,
            onTransactionClick = { }
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (uiState.insights.isNotEmpty()) {
            InsightsSection(
                insights       = uiState.insights,
                onViewAllClick = onViewAllInsightsClick,
                onInsightClick = { insight ->
                    selectedInsight  = insight
                    showInsightSheet = true
                }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showInsightSheet && selectedInsight != null) {
        InsightDetailSheet(
            insight   = selectedInsight!!,
            onDismiss = { showInsightSheet = false }
        )
    }
}
