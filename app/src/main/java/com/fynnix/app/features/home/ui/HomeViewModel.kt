package com.fynnix.app.features.home.ui

import android.app.Application
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fynnix.app.core.analytics.InsightGenerator
import com.fynnix.app.core.analytics.SubscriptionDetector
import com.fynnix.app.core.analytics.TimeRange
import com.fynnix.app.core.analytics.TimeRanges
import com.fynnix.app.core.data.UserPreferences
import com.fynnix.app.core.sms.SyncWorker
import com.fynnix.app.core.utils.TransactionType
import com.fynnix.app.features.budgets.data.BudgetDao
import com.fynnix.app.features.budgets.ui.BudgetUiModel
import com.fynnix.app.features.categories.data.CategoryDao
import com.fynnix.app.features.insights.ui.InsightUiModel
import com.fynnix.app.features.insights.ui.SpendCardData
import com.fynnix.app.features.transactions.data.TransactionDao
import com.fynnix.app.features.transactions.data.TransactionRepository
import com.fynnix.app.features.transactions.ui.TransactionUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** Single immutable snapshot of everything HomeScreen needs to render. */
data class HomeUiState(
    val transactions: List<TransactionUiModel>  = emptyList(),
    val spendCards:   List<SpendCardData>        = emptyList(),
    val budgets:      List<BudgetUiModel>        = emptyList(),
    val insights:     List<InsightUiModel>        = emptyList(),
    val streak:       Int                         = 0,
    val totalSpent:   Double                      = 0.0,
    val isLoading:    Boolean                     = true,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    application: Application,
    private val repository: TransactionRepository,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val budgetDao: BudgetDao,
    private val userPreferences: UserPreferences,
    private val insightGenerator: InsightGenerator,
    private val subscriptionDetector: SubscriptionDetector,
) : AndroidViewModel(application) {

    val syncProgress = com.fynnix.app.core.sms.SmsProcessingManager.processingProgress
    val userName: String get() = userPreferences.getUserName()

    fun manualSync() {
        SyncWorker.scheduleManualSync(getApplication(), userPreferences.getSyncDays())
    }

    private val todayTick  = TimeRanges.ticking(TimeRanges::today)
    private val weekTick   = TimeRanges.ticking(TimeRanges::thisWeek)
    private val monthTick  = TimeRanges.ticking(TimeRanges::thisMonth)
    private val yearTick   = TimeRanges.ticking(TimeRanges::thisYear)

    // ---- individual data streams ----------------------------------------

    private val transactionsFlow = repository.getTransactions()
        .combine(repository.categoryMetaFlow) { list, meta ->
            list.take(8).map { tx ->
                val m = meta[tx.categoryId] ?: Triple("Uncategorized", Icons.Default.SwapHoriz, Color.Gray)
                TransactionUiModel(
                    id            = tx.id,
                    merchantName  = tx.merchant,
                    categoryName  = m.first,
                    categoryIcon  = m.second,
                    categoryColor = m.third,
                    amount        = tx.amount,
                    dateTime      = tx.dateTime,
                    type          = tx.type,
                    isReviewed    = tx.isReviewed,
                    source        = tx.source.name,
                    rawMessage    = tx.rawMessage,
                    messageSource = tx.messageSource,
                )
            }
        }

    private val spendCardsFlow = combine(
        todayTick.flatMapLatest  { rangeSumDebit(it) },
        weekTick.flatMapLatest   { rangeSumDebit(it) },
        monthTick.flatMapLatest  { rangeSumDebit(it) },
        yearTick.flatMapLatest   { rangeSumDebit(it) },
    ) { today, week, month, year ->
        val income = userPreferences.getUserIncome()
        listOf(
            SpendCardData("SPENT THIS MONTH", month, 0.0, income, lastSyncedLabel()),
            SpendCardData("SPENT THIS WEEK",  week,  0.0, null,   lastSyncedLabel()),
            SpendCardData("SPENT TODAY",       today, 0.0, null,   lastSyncedLabel()),
            SpendCardData("SPENT THIS YEAR",   year,  0.0, null,   lastSyncedLabel()),
        )
    }

    private val budgetsFlow = monthTick.flatMapLatest { range ->
        combine(
            categoryDao.getAllFlow(),
            budgetDao.getAllBudgets(),
            transactionDao.categorySpendFlow(range.start, range.end),
        ) { categories, budgets, spendRows ->
            val budgetByCat = budgets.associate { it.categoryId to it.limitAmount }
            val spendByCat  = spendRows.associate { it.categoryId to it.total }
            categories
                .filter { it.id != 0L && (budgetByCat[it.id] ?: 0.0) > 0 }
                .sortedBy { it.sortOrder }
                .take(3)
                .map { cat ->
                    BudgetUiModel(
                        categoryName = cat.name,
                        spentAmount  = spendByCat[cat.id]  ?: 0.0,
                        limitAmount  = budgetByCat[cat.id] ?: 0.0,
                    )
                }
        }
    }

    private val insightsFlow = monthTick.flatMapLatest { range ->
        combine(
            transactionDao.getByDateRange(range.start, range.end),
            categoryDao.getAllFlow(),
            transactionDao.recurringCandidatesFlow(),
        ) { monthTx, categories, candidateRows ->
            val merchants = candidateRows.map { it.merchant }
            val subs      = subscriptionDetector.buildFromCandidates(merchants)
            insightGenerator.generate(
                monthTx              = monthTx,
                categoryNames        = categories.associate { it.id to it.name },
                monthlyIncome        = userPreferences.getUserIncome(),
                subscriptionCount    = subs.size,
                subscriptionMonthlyTotal = subs.sumOf { it.amount },
            ).take(3)
        }
    }

    private val streakFlow = repository.getTransactions().map { list ->
        val days = list
            .filter { it.type == TransactionType.DEBIT }
            .map { startOfDay(it.dateTime) }
            .toSortedSet()
            .toList()
            .reversed()
        countConsecutive(days)
    }

    private val totalSpentFlow = monthTick.flatMapLatest { range ->
        transactionDao.sumDebitsFlow(range.start, range.end)
    }

    // ---- single combined state ------------------------------------------

    val uiState = combine(
        transactionsFlow,
        spendCardsFlow,
        budgetsFlow,
        insightsFlow,
        combine(streakFlow, totalSpentFlow) { streak, total -> streak to total },
    ) { transactions, spendCards, budgets, insights, (streak, total) ->
        HomeUiState(
            transactions = transactions,
            spendCards   = spendCards,
            budgets      = budgets,
            insights     = insights,
            streak       = streak,
            totalSpent   = total,
            isLoading    = false,
        )
    }.stateIn(
        scope         = viewModelScope,
        started       = SharingStarted.WhileSubscribed(5_000),
        initialValue  = HomeUiState(),
    )

    // ---- helpers --------------------------------------------------------

    private fun rangeSumDebit(range: TimeRange) =
        transactionDao.sumDebitsFlow(range.start, range.end)

    private fun lastSyncedLabel(): String {
        val ts = userPreferences.getLastSyncTimestamp()
        if (ts <= 0) return "Just now"
        return SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(ts))
    }

    private fun startOfDay(ts: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = ts }
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0);      cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun countConsecutive(daysDesc: List<Long>): Int {
        if (daysDesc.isEmpty()) return 0
        val today     = startOfDay(System.currentTimeMillis())
        val yesterday = startOfDay(today - 1)
        if (daysDesc.first() != today && daysDesc.first() != yesterday) return 0
        var streak = 1
        for (i in 1 until daysDesc.size) {
            val expected = startOfDay(daysDesc[i - 1] - 1)
            if (daysDesc[i] == expected) streak++ else break
        }
        return streak
    }
}
