package com.fynnix.app.features.accounts.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.fynnix.app.core.analytics.TimeRanges
import com.fynnix.app.core.database.AppDatabase
import com.fynnix.app.core.ui.theme.Primary
import com.fynnix.app.core.utils.AccountSource
import com.fynnix.app.core.utils.AccountType
import com.fynnix.app.features.accounts.data.AccountDao
import com.fynnix.app.features.transactions.data.TransactionDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class AccountManagementViewModel @Inject constructor(
    private val dao: AccountDao,
    private val transactionDao: TransactionDao,
    private val database: AppDatabase,
) : ViewModel() {

    private val monthRange = TimeRanges.thisMonth()

    val accounts = combine(
        dao.getAllAccounts(),
        transactionDao.accountSpendFlow(monthRange.start, monthRange.end),
        transactionDao.accountIncomeFlow(monthRange.start, monthRange.end),
    ) { accs, spendRows, incomeRows ->
        val spendMap  = spendRows.associate  { it.accountId to it.total }
        val incomeMap = incomeRows.associate { it.accountId to it.total }
        accs.map { acc ->
            AccountUiModel(
                id             = acc.id,
                bankName       = acc.bankName,
                type           = when (acc.type) {
                    AccountType.SAVINGS     -> "Savings"
                    AccountType.CREDIT_CARD -> "Credit Card"
                    AccountType.WALLET      -> "Wallet"
                    AccountType.UPI         -> "UPI"
                },
                rawType        = acc.type,
                lastFourDigits = acc.lastFourDigits.takeIf { it.isNotBlank() },
                source         = AccountSource.AUTO,
                balance        = acc.balance,
                updatedAt      = acc.updatedAt,
                monthlySpend   = spendMap[acc.id]  ?: 0.0,
                monthlyIncome  = incomeMap[acc.id] ?: 0.0,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Groups of accounts that share the same (last4, type) — likely duplicates. */
    val duplicateGroups: List<List<AccountUiModel>>
        get() {
            val current = accounts.value
            return current
                .filter { it.lastFourDigits?.isNotBlank() == true }
                .groupBy { "${it.lastFourDigits}_${it.rawType}" }
                .values
                .filter { it.size > 1 }
                .toList()
        }

    fun mergeInto(keepId: Long, discardId: Long) {
        viewModelScope.launch {
            // Reassign-then-delete must be atomic: a crash between the two steps would
            // otherwise leave transactions pointing at an account id that's already gone.
            database.withTransaction {
                transactionDao.reassignAccount(discardId, keepId)
                dao.deleteById(discardId)
            }
        }
    }

    /** Auto-merge: for each duplicate group keep the account with the "best" bank name. */
    fun mergeAllDuplicates() {
        viewModelScope.launch {
            val current = accounts.value
            val groups = current
                .filter { it.lastFourDigits?.isNotBlank() == true }
                .groupBy { "${it.lastFourDigits}_${it.rawType}" }
                .values
                .filter { it.size > 1 }

            database.withTransaction {
                groups.forEach { group ->
                    val keep = group.maxByOrNull { rankBankName(it.bankName) } ?: return@forEach
                    group.filter { it.id != keep.id }.forEach { discard ->
                        transactionDao.reassignAccount(discard.id, keep.id)
                        dao.deleteById(discard.id)
                    }
                }
            }
        }
    }

    private fun rankBankName(name: String): Int = when {
        name == "Other" || name == "UPI" || name == "Wallet" -> 0
        else -> 1
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountManagementScreen(onBack: () -> Unit) {
    val viewModel: AccountManagementViewModel = hiltViewModel()
    val accounts by viewModel.accounts.collectAsState()
    val duplicateGroups = viewModel.duplicateGroups
    var showMergeDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Accounts", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (duplicateGroups.isNotEmpty()) {
                        IconButton(onClick = { showMergeDialog = true }) {
                            Icon(Icons.AutoMirrored.Filled.MergeType, contentDescription = "Merge duplicates", tint = Color(0xFFF59E0B))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (accounts.isEmpty()) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No accounts detected yet.\nThey'll appear automatically once we parse your bank SMS.",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Duplicate-accounts warning
            if (duplicateGroups.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF59E0B).copy(alpha = 0.12f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${duplicateGroups.size} possible duplicate account${if (duplicateGroups.size > 1) "s" else ""} detected",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color.White,
                                )
                                Text(
                                    "Same card/account detected from different bank names",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.7f),
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = { showMergeDialog = true }) {
                                Text("Merge", color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            items(accounts) { acc -> AccountRow(acc) }
        }
    }

    if (showMergeDialog) {
        AlertDialog(
            onDismissRequest = { showMergeDialog = false },
            icon = { Icon(Icons.AutoMirrored.Filled.MergeType, contentDescription = null, tint = Color(0xFFF59E0B)) },
            title = { Text("Merge duplicate accounts?") },
            text = {
                Text(
                    "Found ${duplicateGroups.size} set${if (duplicateGroups.size > 1) "s" else ""} of possible duplicates. " +
                        "Merging keeps the best-named account and reassigns all its transactions. This cannot be undone.",
                    color = Color.Gray,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.mergeAllDuplicates()
                    showMergeDialog = false
                }) { Text("Merge All", color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showMergeDialog = false }) { Text("Cancel") }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}

@Composable
private fun AccountRow(acc: AccountUiModel) {
    val icon: ImageVector = when (acc.rawType) {
        AccountType.SAVINGS     -> Icons.Default.AccountBalance
        AccountType.CREDIT_CARD -> Icons.Default.CreditCard
        AccountType.WALLET      -> Icons.Default.AccountBalanceWallet
        AccountType.UPI         -> Icons.Default.QrCode
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = acc.bankName + (acc.lastFourDigits?.let { " ••$it" } ?: ""),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )
                    Text(acc.type, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    if (acc.updatedAt > 0) {
                        Text(
                            "Updated " + SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(acc.updatedAt)),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray.copy(alpha = 0.7f),
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (acc.rawType == AccountType.CREDIT_CARD) "Avl Limit" else "Balance",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                    )
                    Text(
                        "₹${"%,.0f".format(acc.balance)}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )
                }
            }

            // Monthly stats row
            if (acc.monthlySpend > 0 || acc.monthlyIncome > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    MonthStat(
                        label = "This month spent",
                        value = "₹${"%,.0f".format(acc.monthlySpend)}",
                        color = Color(0xFFEF4444),
                        modifier = Modifier.weight(1f),
                    )
                    if (acc.monthlyIncome > 0) {
                        MonthStat(
                            label = "This month received",
                            value = "₹${"%,.0f".format(acc.monthlyIncome)}",
                            color = Color(0xFF22C55E),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = color)
    }
}
