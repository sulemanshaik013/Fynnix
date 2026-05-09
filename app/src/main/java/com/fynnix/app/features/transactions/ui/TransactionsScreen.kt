package com.fynnix.app.features.transactions.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
    var showTransactionForm by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<TransactionUiModel?>(null) }
    var selectedTransaction by remember { mutableStateOf<TransactionUiModel?>(null) }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }

    val viewModel: TransactionsViewModel = hiltViewModel()
    val transactions by viewModel.transactions.collectAsState()
    val searchQuery by viewModel.query.collectAsState()
    val activeFilter by viewModel.filter.collectAsState()
    val activeSortOrder by viewModel.sortOrder.collectAsState()
    val categories by viewModel.categoryItems.collectAsState()

    val unreviewedCount = transactions.count { !it.isReviewed }

    LaunchedEffect(selectedTransaction?.id) {
        selectedTransaction?.takeIf { !it.isReviewed }?.let {
            viewModel.markReviewed(it.id)
        }
    }

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
                    fontSize = 28.sp
                ),
                color = Color.White
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Sort button
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(
                            Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Sort",
                            tint = if (activeSortOrder != SortOrder.NEWEST_FIRST) Primary else Color.Gray,
                        )
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                    ) {
                        SortOrder.values().forEach { order ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        order.label,
                                        color = if (order == activeSortOrder) Primary else Color.White,
                                        fontWeight = if (order == activeSortOrder) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                                onClick = { viewModel.setSortOrder(order); showSortMenu = false },
                                leadingIcon = {
                                    if (order == activeSortOrder) {
                                        Icon(Icons.Default.Check, null, tint = Primary, modifier = Modifier.size(16.dp))
                                    }
                                },
                            )
                        }
                    }
                }
                // Add new button
                OutlinedButton(
                    onClick = { editingTransaction = null; showTransactionForm = true },
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Primary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setQuery(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            placeholder = { Text("Search merchant, amount or SMS…", color = Color.Gray) },
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
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
            )
        )

        // Filter chips
        LazyRow(
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
                onClick = { viewModel.setFilter(TransactionFilter.NEEDS_REVIEW) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                color = Primary,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "$unreviewedCount transaction${if (unreviewedCount > 1) "s" else ""} need review",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.Black,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Black)
                }
            }
        }

        // Active sort label (shown when non-default)
        if (activeSortOrder != SortOrder.NEWEST_FIRST) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Filled.Sort, null, tint = Primary, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "Sorted by: ${activeSortOrder.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Reset",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    modifier = Modifier.clickable { viewModel.setSortOrder(SortOrder.NEWEST_FIRST) },
                )
            }
        }

        // Transactions List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            if (transactions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillParentMaxSize().padding(top = 60.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.AutoMirrored.Filled.ReceiptLong,
                                null,
                                tint = Color.Gray.copy(alpha = 0.4f),
                                modifier = Modifier.size(56.dp),
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("No transactions found", color = Color.White, style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Try a different filter or search term", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                return@LazyColumn
            }

            // Group by the actual calendar day, not by the rendered label. Grouping by
            // label would merge transactions that share a day-of-month/month across
            // different years into one visual block, making unrelated old transactions
            // appear interleaved with recent ones under the same header.
            val grouped: List<Pair<Long, List<TransactionUiModel>>> = when (activeSortOrder) {
                SortOrder.AMOUNT_HIGH, SortOrder.AMOUNT_LOW -> {
                    // For amount sorts, don't group by date — show as a flat list under a single group
                    listOf(-1L to transactions)
                }
                else -> transactions.groupBy { startOfDay(it.dateTime) }.toList()
            }

            grouped.forEach { (dayKey, txList) ->
                val dateLabel = if (dayKey == -1L) "Sorted by amount" else formatDateHeader(dayKey)
                item(key = dayKey) {
                    val dayDebits = txList.sumOf { if (it.type == TransactionType.DEBIT) it.amount else 0.0 }
                    val dayCredits = txList.sumOf { if (it.type == TransactionType.CREDIT) it.amount else 0.0 }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = dateLabel,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                            ),
                            color = Color.Gray,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (dayCredits > 0) {
                                Text(
                                    text = "+₹${formatAmount(dayCredits)}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = Color(0xFF22C55E),
                                )
                            }
                            if (dayDebits > 0) {
                                Text(
                                    text = "−₹${formatAmount(dayDebits)}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = Color.Gray,
                                )
                            }
                        }
                    }
                }

                items(txList, key = { it.id }) { transaction ->
                    TransactionListItem(
                        transaction = transaction,
                        onClick = { selectedTransaction = transaction },
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Transaction form (new or edit)
    if (showTransactionForm) {
        TransactionFormSheet(
            categories = categories,
            editing = editingTransaction,
            onDismiss = { showTransactionForm = false; editingTransaction = null },
            onSave = { amount, merchant, categoryId, dateTime, type, note ->
                if (editingTransaction != null) {
                    viewModel.updateTransaction(editingTransaction!!.id, amount, merchant, categoryId, dateTime, type, note)
                } else {
                    viewModel.saveManualTransaction(amount, merchant, categoryId, dateTime, type, note)
                }
            },
        )
    }

    if (selectedTransaction != null) {
        TransactionDetailSheet(
            transaction = selectedTransaction!!,
            onDismiss = { selectedTransaction = null },
            onEdit = {
                editingTransaction = selectedTransaction
                selectedTransaction = null
                showTransactionForm = true
            },
            onDelete = {
                pendingDeleteId = selectedTransaction!!.id
                selectedTransaction = null
            },
        )
    }

    if (pendingDeleteId != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete transaction?") },
            text = { Text("This transaction will be removed from your history.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTransaction(pendingDeleteId!!)
                    pendingDeleteId = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteId = null }) { Text("Cancel") } },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}

// ── Transaction form sheet (new + edit) ──────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionFormSheet(
    categories: List<CategoryPickerItem>,
    editing: TransactionUiModel? = null,
    onDismiss: () -> Unit,
    onSave: (amount: Double, merchant: String, categoryId: Long, dateTime: Long, type: TransactionType, note: String?) -> Unit,
) {
    var amount by remember {
        mutableStateOf(
            editing?.amount?.let {
                if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
            } ?: ""
        )
    }
    var merchant by remember { mutableStateOf(editing?.merchantName ?: "") }
    var selectedCategoryId by remember { mutableStateOf(editing?.categoryId ?: 0L) }
    var dateTime by remember { mutableStateOf(editing?.dateTime ?: System.currentTimeMillis()) }
    var txType by remember { mutableStateOf(editing?.type ?: TransactionType.DEBIT) }
    var note by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }

    val isFormValid = amount.isNotBlank() && (amount.toDoubleOrNull()?.let { it > 0 } == true) && merchant.isNotBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())   // scroll when keyboard pushes content
                .imePadding()                             // shrink visible area above keyboard
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
                Text(
                    text = if (editing != null) "Edit Transaction" else "New Transaction",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                TextButton(
                    onClick = {
                        val amt = amount.toDoubleOrNull() ?: return@TextButton
                        onSave(amt, merchant.trim(), selectedCategoryId, dateTime, txType, note.ifBlank { null })
                        onDismiss()
                    },
                    enabled = isFormValid,
                ) {
                    Text("Save", color = if (isFormValid) Primary else Color.Gray, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // DEBIT / CREDIT toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                TransactionType.values().forEach { type ->
                    val selected = type == txType
                    val tColor = if (type == TransactionType.DEBIT) Color(0xFFEF4444) else Color(0xFF22C55E)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { txType = type }
                            .background(if (selected) tColor.copy(alpha = 0.15f) else Color.Transparent)
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (type == TransactionType.DEBIT) "Debit (Spent)" else "Credit (Received)",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) tColor else Color.Gray,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Amount
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("AMOUNT", style = MaterialTheme.typography.labelSmall, color = Color.Gray, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text("₹", style = MaterialTheme.typography.headlineLarge, color = Primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = amount,
                        onValueChange = { v ->
                            if (v.all { it.isDigit() || it == '.' } && v.count { it == '.' } <= 1) amount = v
                        },
                        textStyle = MaterialTheme.typography.displayMedium.copy(
                            color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        cursorBrush = SolidColor(Primary),
                        decorationBox = { inner ->
                            Box {
                                if (amount.isEmpty()) {
                                    Text("0", style = MaterialTheme.typography.displayMedium.copy(
                                        color = Color.Gray.copy(alpha = 0.3f), fontWeight = FontWeight.Bold,
                                    ))
                                }
                                inner()
                            }
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Merchant
            Text("MERCHANT / PAYEE", style = MaterialTheme.typography.labelSmall, color = Color.Gray, letterSpacing = 1.sp)
            TextField(
                value = merchant,
                onValueChange = { merchant = it },
                placeholder = { Text("e.g. Zomato, Netflix", color = Color.Gray) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Gray.copy(alpha = 0.5f),
                    focusedIndicatorColor = Primary,
                    cursorColor = Primary,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                ),
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Category
            if (categories.isNotEmpty()) {
                Text("CATEGORY", style = MaterialTheme.typography.labelSmall, color = Color.Gray, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { cat ->
                        val selected = cat.id == selectedCategoryId
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) cat.color.copy(alpha = 0.2f) else cat.color.copy(alpha = 0.07f),
                            border = BorderStroke(1.dp, if (selected) cat.color else cat.color.copy(alpha = 0.25f)),
                            modifier = Modifier.clickable { selectedCategoryId = if (selected) 0L else cat.id },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(cat.icon, null, modifier = Modifier.size(16.dp), tint = cat.color)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(cat.name, style = MaterialTheme.typography.labelMedium, color = cat.color)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Date
            Text("DATE", style = MaterialTheme.typography.labelSmall, color = Color.Gray, letterSpacing = 1.sp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { showDatePicker = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.CalendarToday, null, tint = Primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(formatDisplayDate(dateTime), color = Color.White, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ChevronRight, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Note
            Text("NOTE (OPTIONAL)", style = MaterialTheme.typography.labelSmall, color = Color.Gray, letterSpacing = 1.sp)
            TextField(
                value = note,
                onValueChange = { note = it },
                placeholder = { Text("Add a note…", color = Color.Gray) },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Gray.copy(alpha = 0.5f),
                    focusedIndicatorColor = Primary,
                    cursorColor = Primary,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                ),
            )
        }
    }

    if (showDatePicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = dateTime)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { dateTime = it }
                    showDatePicker = false
                }) { Text("OK", color = Primary) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = dpState) }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun formatAmount(amount: Double): String =
    if (amount >= 1_00_000) String.format(Locale.US, "%,.0f", amount)
    else String.format(Locale.US, "%,.0f", amount)

private fun formatDisplayDate(ts: Long): String {
    if (ts <= 0) return "Today"
    return SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(Date(ts))
}

// Reused instead of allocating a new SimpleDateFormat per header per recomposition.
private val dayFormatSameYear = SimpleDateFormat("d MMM", Locale.getDefault())
private val dayFormatOtherYear = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

/** [dayStart] must be a start-of-day timestamp, as produced by [startOfDay]. */
private fun formatDateHeader(dayStart: Long): String {
    val now = System.currentTimeMillis()
    val today = startOfDay(now)
    val yesterday = startOfDay(today - 86400000L)
    // Years other than the current one must be shown explicitly — otherwise a
    // same-day-of-year transaction from a prior year is indistinguishable from
    // a recent one and visually collapses into the same group.
    val sameYear = isSameYear(dayStart, now)
    val dayFormat = if (sameYear) dayFormatSameYear else dayFormatOtherYear
    val label = dayFormat.format(Date(dayStart)).uppercase()
    return when (dayStart) {
        today -> "TODAY  ·  $label"
        yesterday -> "YESTERDAY  ·  $label"
        else -> label
    }
}

private fun startOfDay(ts: Long): Long {
    val cal = Calendar.getInstance().apply { timeInMillis = ts }
    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

private fun isSameYear(t1: Long, t2: Long): Boolean {
    val cal1 = Calendar.getInstance().apply { timeInMillis = t1 }
    val cal2 = Calendar.getInstance().apply { timeInMillis = t2 }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
}
