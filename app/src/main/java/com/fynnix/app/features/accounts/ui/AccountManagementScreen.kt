package com.fynnix.app.features.accounts.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.QrCode
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
import com.fynnix.app.core.ui.theme.Primary
import com.fynnix.app.core.utils.AccountSource
import com.fynnix.app.core.utils.AccountType
import com.fynnix.app.features.accounts.data.AccountDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class AccountManagementViewModel @Inject constructor(
    private val dao: AccountDao,
) : ViewModel() {
    val accounts = dao.getAllAccounts()
        .map { list ->
            list.map {
                AccountUiModel(
                    id = it.id,
                    bankName = it.bankName,
                    type = when (it.type) {
                        AccountType.SAVINGS -> "Savings"
                        AccountType.CREDIT_CARD -> "Credit Card"
                        AccountType.WALLET -> "Wallet"
                        AccountType.UPI -> "UPI"
                    },
                    rawType = it.type,
                    lastFourDigits = it.lastFourDigits.takeIf { d -> d.isNotBlank() },
                    source = AccountSource.AUTO,
                    balance = it.balance,
                    updatedAt = it.updatedAt,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountManagementScreen(onBack: () -> Unit) {
    val viewModel: AccountManagementViewModel = hiltViewModel()
    val accounts by viewModel.accounts.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Accounts", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            items(accounts) { acc -> AccountRow(acc) }
        }
    }
}

@Composable
private fun AccountRow(acc: AccountUiModel) {
    val icon: ImageVector = when (acc.rawType) {
        AccountType.SAVINGS -> Icons.Default.AccountBalance
        AccountType.CREDIT_CARD -> Icons.Default.CreditCard
        AccountType.WALLET -> Icons.Default.AccountBalanceWallet
        AccountType.UPI -> Icons.Default.QrCode
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = acc.bankName + (acc.lastFourDigits?.let { " ••$it" } ?: ""),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Text(
                    text = acc.type,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
                if (acc.updatedAt > 0) {
                    Text(
                        text = "Updated " + SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(acc.updatedAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray.copy(alpha = 0.7f),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (acc.rawType == AccountType.CREDIT_CARD) "Avl Limit" else "Balance",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                )
                Text(
                    text = "₹${"%,.0f".format(acc.balance)}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
            }
        }
    }
}
