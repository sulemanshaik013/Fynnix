package com.fynnix.app.features.home.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.fynnix.app.core.ui.theme.CardCornerRadius
import com.fynnix.app.features.budgets.ui.components.BudgetSection
import com.fynnix.app.features.home.ui.components.HomeTopBar
import com.fynnix.app.features.home.ui.components.ReceivedSentRow
import com.fynnix.app.features.home.ui.components.RecentTransactionsSection
import com.fynnix.app.features.home.ui.components.SpendCardPager
import com.fynnix.app.features.home.ui.components.StreakBanner
import com.fynnix.app.features.home.ui.components.UpcomingSection
import com.fynnix.app.features.insights.ui.InsightUiModel
import com.fynnix.app.features.insights.ui.components.InsightDetailSheet
import com.fynnix.app.features.insights.ui.components.InsightsSection
import com.fynnix.app.features.transactions.ui.TransactionUiModel
import com.fynnix.app.features.transactions.ui.components.TransactionDetailSheet

@Composable
fun HomeScreen(
    onSeeAllBudgetsClick: () -> Unit,
    onViewAllTransactionsClick: () -> Unit,
    onViewAllInsightsClick: () -> Unit,
    onAiChatClick: () -> Unit,
) {
    val viewModel: HomeViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val syncProgress by viewModel.syncProgress.collectAsState()

    var selectedInsight by remember { mutableStateOf<InsightUiModel?>(null) }
    var showInsightSheet by remember { mutableStateOf(false) }
    var selectedTransaction by remember { mutableStateOf<TransactionUiModel?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        HomeTopBar(
            name             = viewModel.userName,
            onRefreshClick   = { viewModel.manualSync() },
            onNotificationsClick = { },
            onAiChatClick    = onAiChatClick
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
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                shape  = RoundedCornerShape(CardCornerRadius),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.12f)),
            ) {
                Row(
                    modifier          = Modifier.padding(14.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "SMS access is off",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "Auto-tracking is paused. Tap to enable.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444)),
                    ) {
                        Text("Enable", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
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
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                color    = MaterialTheme.colorScheme.primary,
            )
        }

        if (uiState.streak > 0) StreakBanner(streakCount = uiState.streak)

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.spendCards.isNotEmpty()) {
            SpendCardPager(cards = uiState.spendCards)
            Spacer(modifier = Modifier.height(12.dp))
            ReceivedSentRow(received = uiState.totalReceived, sent = uiState.totalSpent)
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (uiState.upcoming.isNotEmpty()) {
            UpcomingSection(items = uiState.upcoming)
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (uiState.budgets.isNotEmpty()) {
            BudgetSection(budgets = uiState.budgets, onSeeAllClick = onSeeAllBudgetsClick)
            Spacer(modifier = Modifier.height(24.dp))
        }

        RecentTransactionsSection(
            transactions  = uiState.transactions,
            onViewAllClick = onViewAllTransactionsClick,
            onTransactionClick = { selectedTransaction = it }
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

    selectedTransaction?.let { transaction ->
        LaunchedEffect(transaction.id) {
            if (!transaction.isReviewed) viewModel.markReviewed(transaction.id)
        }
        TransactionDetailSheet(
            transaction = transaction,
            onDismiss = { selectedTransaction = null },
            onEdit = {
                selectedTransaction = null
                onViewAllTransactionsClick()
            },
            onDelete = {
                viewModel.deleteTransaction(transaction.id)
                selectedTransaction = null
            },
            onSplitReimbursementChange = { isSplit, splitAmount, isReimbursement ->
                viewModel.setSplitReimbursement(transaction.id, isSplit, splitAmount, isReimbursement)
                selectedTransaction = transaction.copy(
                    isSplit = isSplit, splitAmount = splitAmount, isReimbursement = isReimbursement,
                )
            },
        )
    }
}
