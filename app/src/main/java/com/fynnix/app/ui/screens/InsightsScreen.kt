package com.fynnix.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fynnix.app.ui.components.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(onEditAccountsClick: () -> Unit) {
    var selectedDate by remember { mutableStateOf(Calendar.getInstance()) }
    var filterPeriod by remember { mutableStateOf("This Month") }
    var showDatePicker by remember { mutableStateOf(false) }
    
    var selectedInsight by remember { mutableStateOf<InsightUiModel?>(null) }
    var showInsightSheet by remember { mutableStateOf(false) }

    val filterOptions = listOf("Today", "This Week", "This Month", "This Year", "Custom")

    val mockAccounts = listOf(
        AccountUiModel(1, "HDFC Bank", "Savings", "4521", AccountSource.AUTO, 45200.0),
        AccountUiModel(2, "ICICI Bank", "Credit Card", "8892", AccountSource.AUTO, 12500.0),
        AccountUiModel(3, "Cash", "Wallet", null, AccountSource.MANUAL, 1200.0)
    )

    val mockDonutData = listOf(
        CategorySpend("Food", 5500.0, 0.45f, Color(0xFFEF4444)),
        CategorySpend("Rent", 12000.0, 0.35f, Color(0xFF3B82F6)),
        CategorySpend("Transport", 2500.0, 0.12f, Color(0xFFF59E0B)),
        CategorySpend("Other", 1500.0, 0.08f, Color(0xFF10B981))
    )

    val mockInsights = listOf(
        InsightUiModel(
            id = "1", text = "Monthly cashflow looks healthy",
            icon = Icons.Default.CheckCircle, iconColor = Color(0xFF22C55E),
            detailTitle = "Monthly cashflow looks healthy", detailTag = "% of income spent",
            detailValue = "62% of income spent", detailDescription = "You've spent 62% of your income this month. That leaves a comfortable buffer for savings.",
            actionText = "Set budget limit",
            severity = InsightSeverity.NEUTRAL
        ),
        InsightUiModel(
            id = "2", text = "Food budget exceeded",
            icon = Icons.Default.Warning, iconColor = Color(0xFFEF4444),
            detailTitle = "Food budget exceeded", detailTag = "Over budget",
            detailValue = "₹2,500 over limit", detailDescription = "Your spending in the Food category has exceeded the monthly limit of ₹5,500.",
            actionText = "Adjust budget",
            severity = InsightSeverity.NEUTRAL
        ),
        InsightUiModel(
            id = "3", text = "4 active subscriptions detected",
            icon = Icons.Default.Info, iconColor = Color(0xFFF59E0B),
            detailTitle = "4 active subscriptions", detailTag = "Recurring spend",
            detailValue = "₹1,499 monthly", detailDescription = "We've detected 4 recurring payments. You can review them in the subscriptions tab.",
            actionText = "Review subscriptions",
            severity = InsightSeverity.NEUTRAL
        ),
        InsightUiModel(
            id = "4", text = "Savings rate is strong",
            icon = Icons.AutoMirrored.Filled.TrendingUp, iconColor = Color(0xFF22C55E),
            detailTitle = "Savings rate is strong", detailTag = "Savings",
            detailValue = "22% saved", detailDescription = "You have saved 22% of your income this month, which is above your average of 15%.",
            actionText = "View savings goal",
            severity = InsightSeverity.NEUTRAL
        ),
        InsightUiModel(
            id = "5", text = "Late-night spending pattern",
            icon = Icons.Default.NightsStay, iconColor = Color(0xFF818CF8),
            detailTitle = "Late-night spending", detailTag = "Behaviour",
            detailValue = "15% of spend after 11PM", detailDescription = "We noticed an increase in transactions between 11 PM and 3 AM recently.",
            actionText = "Set curfew alert",
            severity = InsightSeverity.NEUTRAL
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        InsightsTopBar(
            selectedDate = selectedDate,
            onMonthClick = { showDatePicker = true }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                MyAccountsSection(
                    accounts = mockAccounts,
                    onEditClick = onEditAccountsClick
                )
            }

            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(filterOptions) { period ->
                        FilterChip(
                            selected = filterPeriod == period,
                            onClick = { filterPeriod = period },
                            label = { Text(period) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.Black
                            )
                        )
                    }
                }
            }

            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "WHERE YOUR MONEY GOES",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    
                    DonutChart(
                        data = mockDonutData,
                        totalAmount = 21500.0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                    )
                    
                    DonutChartLegend(data = mockDonutData)
                }
            }

            // Component 5: Behavioural Patterns
            item {
                Text(
                    text = "BEHAVIORAL PATTERNS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
            
            items(mockInsights) { insight ->
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    InsightCard(
                        insight = insight, 
                        onClick = {
                            selectedInsight = insight
                            showInsightSheet = true
                        }
                    )
                }
            }
        }
    }

    if (showInsightSheet && selectedInsight != null) {
        InsightDetailSheet(
            insight = selectedInsight!!,
            onDismiss = { showInsightSheet = false }
        )
    }

    if (showDatePicker) {
        AlertDialog(
            onDismissRequest = { showDatePicker = false },
            title = { Text("Select Month") },
            text = {
                val months = remember {
                    val list = mutableListOf<Calendar>()
                    for (i in -2..2) {
                        val cal = selectedDate.clone() as Calendar
                        cal.add(Calendar.MONTH, i)
                        list.add(cal)
                    }
                    list
                }
                Column {
                    months.forEach { month ->
                        TextButton(
                            onClick = {
                                selectedDate = month
                                showDatePicker = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(SimpleDateFormat("MMM yyyy", Locale.US).format(month.time))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun InsightsTopBar(
    selectedDate: Calendar,
    onMonthClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Insights",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )

        Surface(
            onClick = onMonthClick,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f)),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.height(40.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = SimpleDateFormat("MMM yyyy", Locale.US).format(selectedDate.time),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
