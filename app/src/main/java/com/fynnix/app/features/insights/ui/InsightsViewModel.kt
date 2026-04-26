package com.fynnix.app.features.insights.ui


import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fynnix.app.core.analytics.InsightGenerator
import com.fynnix.app.core.analytics.SubscriptionDetector
import com.fynnix.app.core.analytics.TimeRange
import com.fynnix.app.core.analytics.TimeRanges
import com.fynnix.app.core.data.UserPreferences
import com.fynnix.app.core.utils.AccountSource
import com.fynnix.app.features.accounts.data.AccountDao
import com.fynnix.app.features.accounts.ui.AccountUiModel
import com.fynnix.app.features.categories.data.CategoryDao
import com.fynnix.app.features.categories.ui.CategorySpend
import com.fynnix.app.features.transactions.data.TransactionDao
import com.fynnix.app.features.transactions.data.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject


enum class InsightSeverity { POSITIVE, WARNING, NEUTRAL }

data class InsightUiModel(
    val id: String,
    val text: String,
    val icon: ImageVector,
    val iconColor: Color,
    val detailTitle: String,
    val detailDescription: String,
    val detailTag: String,
    val detailValue: String,
    val severity: InsightSeverity,
    val actionText: String? = null,
    val onAction: (() -> Unit)? = null
)

data class SpendCardData(
    val label: String,
    val debitAmount: Double,
    val creditAmount: Double,
    val incomeAmount: Double? = null,
    val lastSynced: String
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val accountDao: AccountDao,
    private val repository: TransactionRepository,
    private val insightGenerator: InsightGenerator,
    private val subscriptionDetector: SubscriptionDetector,
    private val userPrefs: UserPreferences,
) : ViewModel() {

    /** Currently selected reporting period — driven by the screen's filter chips / month picker. */
    private val _selectedRange = MutableStateFlow(TimeRanges.thisMonth())
    val selectedRange: kotlinx.coroutines.flow.StateFlow<TimeRange> = _selectedRange

    fun setRange(range: TimeRange) {
        _selectedRange.value = range
    }

    val accounts = accountDao.getAllAccounts()
        .map { accs ->
            accs.map {
                AccountUiModel(
                    id = it.id,
                    bankName = it.bankName,
                    type = when (it.type) {
                        com.fynnix.app.core.utils.AccountType.SAVINGS -> "Savings"
                        com.fynnix.app.core.utils.AccountType.CREDIT_CARD -> "Credit Card"
                        com.fynnix.app.core.utils.AccountType.WALLET -> "Wallet"
                        com.fynnix.app.core.utils.AccountType.UPI -> "UPI"
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

    val categorySpend = _selectedRange
        .flatMapLatest { range ->
            combine(
                transactionDao.categorySpendFlow(range.start, range.end),
                repository.categoryMetaFlow,
            ) { rows, meta ->
                val total = rows.sumOf { it.total }
                if (total <= 0) emptyList()
                else rows.map { row ->
                    val m = meta[row.categoryId]
                    CategorySpend(
                        categoryName = m?.first ?: "Uncategorized",
                        amount = row.total,
                        percentage = (row.total / total).toFloat(),
                        color = m?.third ?: Color.Gray,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalSpend = _selectedRange
        .flatMapLatest { range -> transactionDao.sumDebitsFlow(range.start, range.end) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val insights = _selectedRange
        .flatMapLatest { range ->
            combine(
                transactionDao.getByDateRange(range.start, range.end),
                categoryDao.getAllFlow(),
                transactionDao.recurringCandidatesFlow(),
            ) { monthTx, categories, candidateRows ->
                val merchants = candidateRows.map { it.merchant }
                val subs = subscriptionDetector.buildFromCandidates(merchants)
                insightGenerator.generate(
                    monthTx = monthTx,
                    categoryNames = categories.associate { it.id to it.name },
                    monthlyIncome = userPrefs.getUserIncome(),
                    subscriptionCount = subs.size,
                    subscriptionMonthlyTotal = subs.sumOf { it.amount },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<InsightUiModel>())
}
