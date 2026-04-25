package com.fynnix.app.features.transactions.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fynnix.app.core.ui.theme.Primary
import com.fynnix.app.core.utils.TransactionType
import androidx.hilt.navigation.compose.hiltViewModel
import com.fynnix.app.features.transactions.ui.components.TransactionDetailSheet
import com.fynnix.app.features.transactions.ui.components.TransactionListItem
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen() {
    var showNewTransactionSheet by remember { mutableStateOf(false) }
    var selectedTransaction by remember { mutableStateOf<TransactionUiModel?>(null) }

    val viewModel: TransactionsViewModel = hiltViewModel()
    val transactions by viewModel.transactions.collectAsState()
    val searchQuery by viewModel.query.collectAsState()
    val activeFilter by viewModel.filter.collectAsState()

    val unreviewedCount = transactions.count { !it.isReviewed }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Transactions",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp
                ),
                color = Color.White
            )

            OutlinedButton(
                onClick = { showNewTransactionSheet = true },
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Primary),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add New", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setQuery(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            placeholder = { Text("Search merchant, amount or SMS text…", color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setQuery("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray)
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary,
                unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )

        // Filter chips
        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(TransactionFilter.values().toList()) { filter ->
                FilterChip(
                    selected = filter == activeFilter,
                    onClick = { viewModel.setFilter(filter) },
                    label = { Text(filter.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Primary,
                        selectedLabelColor = Color.Black,
                    ),
                )
            }
        }

        // Needs Review Banner
        if (unreviewedCount > 0) {
            Surface(
                onClick = { /* TODO Filter to unreviewed */ },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                color = Primary
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "$unreviewedCount new transactions need review",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.Black,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Black)
                }
            }
        }

        // Transactions List (filtering already done in VM)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp)
        ) {
            if (transactions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillParentMaxSize().padding(top = 60.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No transactions match these filters",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                return@LazyColumn
            }

            val grouped = transactions.groupBy { formatDateHeader(it.dateTime) }
            
            grouped.forEach { (dateLabel, transactions) ->
                item {
                    val totalForDay = transactions.sumOf { if(it.type == TransactionType.DEBIT) it.amount else 0.0 }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = dateLabel,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = Color.Gray
                        )
                        if (totalForDay > 0) {
                            Text(
                                text = "Total: ₹${String.format(Locale.US, "%,.0f", totalForDay)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                    }
                }
                
                items(transactions, key = { it.id }) { transaction ->
                    TransactionListItem(
                        transaction = transaction,
                        onClick = { selectedTransaction = transaction }
                    )
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Load older transactions...",
                        style = MaterialTheme.typography.labelLarge,
                        color = Primary,
                        modifier = Modifier.clickable { /* TODO Load more */ }
                    )
                }
            }
        }
    }

    if (showNewTransactionSheet) {
        NewTransactionSheet(onDismiss = { showNewTransactionSheet = false })
    }

    if (selectedTransaction != null) {
        TransactionDetailSheet(
            transaction = selectedTransaction!!,
            onDismiss = { selectedTransaction = null },
            onEdit = { /* TODO */ },
            onDelete = { /* TODO */ }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTransactionSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = Color.Gray)
                }
                Text(
                    text = "New Transaction",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                TextButton(onClick = { /* TODO Save logic */ }) {
                    Text("Save", color = Primary, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Amount
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "AMOUNT",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    letterSpacing = 1.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("₹", style = MaterialTheme.typography.headlineLarge, color = Color.Gray)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "0",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 64.sp
                        ),
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Merchant
            Text("MERCHANT / PAYEE", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            TextField(
                value = "",
                onValueChange = {},
                placeholder = { Text("e.g. Starbucks, Zomato", color = Color.Gray) },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Gray.copy(alpha = 0.5f)
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Category
            Text("CATEGORY", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CategoryChip("Food", Icons.Default.Restaurant, Color(0xFFE53935))
                CategoryChip("Transport", Icons.Default.DirectionsCar, Color(0xFF00897B))
                CategoryChip("Rent", Icons.Default.Home, Color(0xFF1E88E5))
                CategoryChip("More...", Icons.Default.GridView, Color.Gray)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Date
            Text("DATE", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Friday, April 3, 2026", color = Color.White)
            }
        }
    }
}

@Composable
fun CategoryChip(label: String, icon: ImageVector, color: Color) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = color)
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}

private fun formatDateHeader(dateTime: Long): String {
    val calendar = Calendar.getInstance()
    val now = calendar.timeInMillis
    
    val date = Date(dateTime)
    val dayFormat = SimpleDateFormat("d MMM", Locale.getDefault())
    
    val dayMillis = 86400000L
    val diff = now - dateTime
    
    return when {
        diff < dayMillis && isSameDay(now, dateTime) -> "TODAY - ${dayFormat.format(date).uppercase()}"
        diff < 2 * dayMillis && isSameDay(now - dayMillis, dateTime) -> "YESTERDAY - ${dayFormat.format(date).uppercase()}"
        else -> dayFormat.format(date).uppercase()
    }
}

private fun isSameDay(t1: Long, t2: Long): Boolean {
    val cal1 = Calendar.getInstance().apply { timeInMillis = t1 }
    val cal2 = Calendar.getInstance().apply { timeInMillis = t2 }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
           cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}
