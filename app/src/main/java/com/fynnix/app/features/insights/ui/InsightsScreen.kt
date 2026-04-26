package com.fynnix.app.features.insights.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fynnix.app.core.analytics.TimeRanges
import com.fynnix.app.features.accounts.ui.components.MyAccountsSection
import com.fynnix.app.features.home.ui.components.DonutChart
import com.fynnix.app.features.home.ui.components.DonutChartLegend
import com.fynnix.app.features.insights.ui.components.InsightCard
import com.fynnix.app.features.insights.ui.components.InsightDetailSheet
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

    val viewModel: InsightsViewModel = hiltViewModel()
    val accounts by viewModel.accounts.collectAsState()
    val donutData by viewModel.categorySpend.collectAsState()
    val totalSpend by viewModel.totalSpend.collectAsState()
    val insights by viewModel.insights.collectAsState()

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
                    accounts = accounts,
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
                            onClick = {
                                filterPeriod = period
                                when (period) {
                                    "Today" -> viewModel.setRange(TimeRanges.today())
                                    "This Week" -> viewModel.setRange(TimeRanges.thisWeek())
                                    "This Month" -> {
                                        selectedDate = Calendar.getInstance()
                                        viewModel.setRange(TimeRanges.thisMonth())
                                    }
                                    "This Year" -> viewModel.setRange(TimeRanges.thisYear())
                                    "Custom" -> showDatePicker = true
                                }
                            },
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
                        data = donutData,
                        totalAmount = totalSpend,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                    )
                    
                    DonutChartLegend(data = donutData)
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
            
            items(insights) { insight ->
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
                                filterPeriod = "Custom"
                                viewModel.setRange(TimeRanges.forMonth(month))
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
