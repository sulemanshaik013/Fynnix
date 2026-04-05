package com.fynnix.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fynnix.app.data.local.entity.TransactionType
import com.fynnix.app.ui.components.*

@Composable
fun HomeScreen(
    onSettingsClick: () -> Unit,
    onSeeAllBudgetsClick: () -> Unit,
    onViewAllTransactionsClick: () -> Unit,
    onViewAllInsightsClick: () -> Unit
) {
    val streakCount by remember { mutableIntStateOf(7) }
    
    var selectedInsight by remember { mutableStateOf<InsightUiModel?>(null) }
    var showInsightSheet by remember { mutableStateOf(false) }

    val mockSpendCards = listOf(
        SpendCardData("SPENT THIS MONTH", 22500.0, 4500.0, 50000.0, "3 Apr, 21:53"),
        SpendCardData("SPENT THIS WEEK", 4200.0, 0.0, 12000.0, "3 Apr, 21:53"),
        SpendCardData("SPENT TODAY", 450.0, 0.0, null, "3 Apr, 21:53"),
        SpendCardData("SPENT THIS YEAR", 145000.0, 12000.0, 600000.0, "3 Apr, 21:53")
    )

    val mockBudgets = listOf(
        BudgetUiModel("Food", 2500.0, 5500.0),
        BudgetUiModel("Transport", 450.0, 3500.0),
        BudgetUiModel("Shopping", 5000.0, 7000.0)
    )

    val mockTransactions = listOf(
        TransactionUiModel(
            1, "Zomato", "Food", Icons.Default.Restaurant, 
            Color(0xFFEF4444), 2500.0, System.currentTimeMillis(), TransactionType.DEBIT
        ),
        TransactionUiModel(
            2, "Uber", "Transport", Icons.Default.DirectionsCar, 
            Color(0xFF3B82F6), 450.0, System.currentTimeMillis() - 86400000, TransactionType.DEBIT
        ),
        TransactionUiModel(
            3, "Amazon", "Shopping", Icons.Default.ShoppingBag, 
            Color(0xFFF59E0B), 1200.0, System.currentTimeMillis() - 172800000, TransactionType.DEBIT
        )
    )

    val mockInsights = listOf(
        InsightUiModel(
            id = "1",
            text = "Monthly cashflow looks healthy",
            icon = Icons.Default.CheckCircle,
            iconColor = Color(0xFF22C55E),
            detailTitle = "Monthly cashflow looks healthy",
            detailTag = "% of income spent",
            detailValue = "62% of income spent",
            detailDescription = "You've spent 62% of your income this month. That leaves a comfortable buffer for savings.",
            actionText = "Set budget limit",
            severity = InsightSeverity.NEUTRAL
        ),
        InsightUiModel(
            id = "2",
            text = "Food budget exceeded",
            icon = Icons.Default.Warning,
            iconColor = Color(0xFFEF4444),
            detailTitle = "Food budget exceeded",
            detailTag = "Over budget",
            detailValue = "₹2,500 over limit",
            detailDescription = "Your spending in the Food category has exceeded the monthly limit of ₹5,500.",
            actionText = "Adjust budget",
            severity = InsightSeverity.NEUTRAL
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        HomeTopBar(
            name = "Vishal",
            onRefreshClick = { /* TODO Handle refresh */ },
            onNotificationsClick = { /* TODO Handle notifications */ },
            onAiChatClick = { /* TODO Handle AI chat */ }
        )

        StreakBanner(streakCount = streakCount)

        Spacer(modifier = Modifier.height(16.dp))

        SpendCardPager(cards = mockSpendCards)
        
        Spacer(modifier = Modifier.height(24.dp))

        BudgetSection(
            budgets = mockBudgets,
            onSeeAllClick = onSeeAllBudgetsClick
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        RecentTransactionsSection(
            transactions = mockTransactions,
            onViewAllClick = onViewAllTransactionsClick,
            onTransactionClick = { /* TODO Handle detail click */ }
        )

        Spacer(modifier = Modifier.height(24.dp))

        InsightsSection(
            insights = mockInsights,
            onViewAllClick = onViewAllInsightsClick,
            onInsightClick = { insight ->
                selectedInsight = insight
                showInsightSheet = true
            }
        )
        
        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showInsightSheet && selectedInsight != null) {
        InsightDetailSheet(
            insight = selectedInsight!!,
            onDismiss = { showInsightSheet = false }
        )
    }
}