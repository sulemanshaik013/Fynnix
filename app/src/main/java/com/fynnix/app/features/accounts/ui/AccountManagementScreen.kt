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
import androidx.compose.material.icons.filled.CardGiftcard
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.fynnix.app.core.analytics.TimeRanges
import com.fynnix.app.core.database.AppDatabase
import com.fynnix.app.core.ui.theme.Primary
import com.fynnix.app.core.utils.AccountSource
import com.fynnix.app.core.utils.AccountType
import com.fynnix.app.core.utils.launchCatching
import com.fynnix.app.features.accounts.data.AccountDao
import com.fynnix.app.features.transactions.data.TransactionDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
                    AccountType.SAVINGS      -> "Savings"
                    AccountType.CREDIT_CARD  -> "Credit Card"
                    AccountType.WALLET       -> "Wallet"
                    AccountType.UPI          -> "UPI"
                    AccountType.BENEFIT_CARD -> "Benefit Card"
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
        get() = computeDuplicateGroups(accounts.value)

    /**
     * Same (last4, type) is not enough to call two accounts duplicates: last-4 digits alone
     * collide often across different banks (only 10,000 possible values), and the real
     * account identity used everywhere else is (bankName, last4, type). This only flags a
     * group when at most one *specific* bank name appears in it — the actual duplicate case
     * this exists for is a single real account whose bank name first resolved to a generic
     * placeholder (e.g. "Other"/"UPI"/"Wallet") and later resolved to the real bank name from
     * a clearer SMS. A group containing two or more distinct specific bank names is left
     * alone, since merging it would silently reassign one bank's transactions onto another.
     */
    private fun computeDuplicateGroups(current: List<AccountUiModel>): List<List<AccountUiModel>> =
        current
            .filter { it.lastFourDigits?.isNotBlank() == true }
            .groupBy { "${it.lastFourDigits}_${it.rawType}" }
            .values
            .filter { it.size > 1 }
            .filter { group -> group.map { it.bankName }.filterNot(::isGenericBankName).toSet().size <= 1 }
            .toList()

    /** Auto-merge: for each duplicate group keep the account with the "best" bank name. */
    fun mergeAllDuplicates() {
        viewModelScope.launchCatching(TAG) {
            val groups = computeDuplicateGroups(accounts.value)

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

    private fun isGenericBankName(name: String) = name == "Other" || name == "UPI" || name == "Wallet"

    private fun rankBankName(name: String): Int = if (isGenericBankName(name)) 0 else 1

    private companion object {
        const val TAG = "AccountManagementVM"
    }
}

private val ACCOUNT_TYPE_ORDER = listOf(
    AccountType.SAVINGS, AccountType.CREDIT_CARD, AccountType.UPI,
    AccountType.WALLET, AccountType.BENEFIT_CARD,
)

private val ACCOUNT_TYPE_SECTION_LABEL = mapOf(
    AccountType.SAVINGS to "BANK ACCOUNTS",
    AccountType.CREDIT_CARD to "CREDIT CARDS",
    AccountType.UPI to "UPI",
    AccountType.WALLET to "WALLETS",
    AccountType.BENEFIT_CARD to "BENEFIT CARDS",
)

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
            item(key = "net-worth") { NetWorthCard(accounts) }

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

            // Grouped by type instead of one flat list, so it's clear at a glance which
            // entries are the user's own money (Savings/UPI) versus employer-funded pots
            // (Benefit Card) or credit (Credit Card) — these aren't fungible with each
            // other and lumping them together made the section read as one undifferentiated
            // pile of "accounts."
            val grouped = accounts.groupBy { it.rawType }
                .toSortedMap(compareBy { ACCOUNT_TYPE_ORDER.indexOf(it) })
            grouped.forEach { (type, accountsOfType) ->
                item(key = "header-$type") {
                    Text(
                        text = ACCOUNT_TYPE_SECTION_LABEL[type] ?: type.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                items(accountsOfType, key = { it.id }) { acc -> AccountRow(acc) }
            }
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

/**
 * Sum of balances the user actually holds: Savings/UPI/Wallet/Benefit Card accounts store a
 * real balance figure, but a credit card's stored "balance" is its *available limit* (see
 * [BalanceExtractor]'s "avl lmt"/"available limit" patterns) — not money owed or held, so
 * including it here would silently inflate net worth by however much credit is left unused.
 */
@Composable
private fun NetWorthCard(accounts: List<AccountUiModel>) {
    val netWorth = accounts
        .filter { it.rawType != AccountType.CREDIT_CARD }
        .sumOf { it.balance }
    val reportedCount = accounts.count { it.rawType != AccountType.CREDIT_CARD && it.balance != 0.0 }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "NET WORTH",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "₹${"%,.0f".format(netWorth)}",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                if (reportedCount > 0)
                    "Across $reportedCount account${if (reportedCount > 1) "s" else ""} with a reported balance · excludes credit limits"
                else
                    "No balance-reporting accounts yet",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
            )
        }
    }
}

@Composable
private fun AccountRow(acc: AccountUiModel) {
    val icon: ImageVector = when (acc.rawType) {
        AccountType.SAVINGS      -> Icons.Default.AccountBalance
        AccountType.CREDIT_CARD  -> Icons.Default.CreditCard
        AccountType.WALLET       -> Icons.Default.AccountBalanceWallet
        AccountType.UPI          -> Icons.Default.QrCode
        AccountType.BENEFIT_CARD -> Icons.Default.CardGiftcard
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
                    if (acc.balance == 0.0) {
                        // A stored 0.0 here is ambiguous — it's the same default used both
                        // for a genuinely empty account and for one where no SMS has ever
                        // reported a balance figure (common for UPI/wallet debits, which
                        // frequently omit it). Showing "₹0" in the latter case reads as a
                        // bug rather than "no data yet", so say so explicitly instead.
                        Text(
                            "Not reported",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray,
                        )
                    } else {
                        Text(
                            "₹${"%,.0f".format(acc.balance)}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                        )
                    }
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
