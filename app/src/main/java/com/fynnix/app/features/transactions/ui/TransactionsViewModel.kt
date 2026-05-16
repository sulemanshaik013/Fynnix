package com.fynnix.app.features.transactions.ui

import android.app.Application
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fynnix.app.core.analytics.TimeRanges
import com.fynnix.app.core.data.UserPreferences
import com.fynnix.app.core.sms.SyncWorker
import com.fynnix.app.core.utils.TransactionSource
import com.fynnix.app.core.utils.TransactionType
import com.fynnix.app.core.utils.launchCatching
import com.fynnix.app.features.transactions.data.Transaction
import com.fynnix.app.features.transactions.data.TransactionDao
import com.fynnix.app.features.transactions.data.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.UUID
import javax.inject.Inject

enum class TransactionFilter(val label: String) {
    ALL("All"),
    DEBITS("Debits"),
    CREDITS("Credits"),
    NEEDS_REVIEW("Needs review"),
}

enum class SortOrder(val label: String) {
    NEWEST_FIRST("Newest first"),
    OLDEST_FIRST("Oldest first"),
    AMOUNT_HIGH("Highest amount"),
    AMOUNT_LOW("Lowest amount"),
}

enum class PeriodFilter(val label: String) {
    ALL_TIME("All time"),
    THIS_MONTH("This month"),
    LAST_MONTH("Last month"),
    THIS_WEEK("This week"),
}

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    application: Application,
    private val transactionRepository: TransactionRepository,
    private val transactionDao: TransactionDao,
    private val userPreferences: UserPreferences,
) : AndroidViewModel(application) {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _filter = MutableStateFlow(TransactionFilter.ALL)
    val filter: StateFlow<TransactionFilter> = _filter

    private val _periodFilter = MutableStateFlow(PeriodFilter.ALL_TIME)
    val periodFilter: StateFlow<PeriodFilter> = _periodFilter

    private val _sortOrder = MutableStateFlow(SortOrder.NEWEST_FIRST)
    val sortOrder: StateFlow<SortOrder> = _sortOrder

    val transactions: StateFlow<List<TransactionUiModel>> =
        combine(
            transactionRepository.getAllTransactions(),
            transactionRepository.categoryMetaFlow,
            _query,
            combine(_filter, _periodFilter) { f, period -> f to period },
            _sortOrder,
        ) { list, meta, q, (f, period), sort ->
            val periodRange = when (period) {
                PeriodFilter.ALL_TIME -> null
                PeriodFilter.THIS_MONTH -> TimeRanges.thisMonth()
                PeriodFilter.LAST_MONTH -> TimeRanges.lastMonth()
                PeriodFilter.THIS_WEEK -> TimeRanges.thisWeek()
            }
            list
                .asSequence()
                .filter { tx -> periodRange == null || tx.dateTime in periodRange.start..periodRange.end }
                .filter { tx ->
                    when (f) {
                        TransactionFilter.ALL -> true
                        TransactionFilter.DEBITS -> tx.type == TransactionType.DEBIT
                        TransactionFilter.CREDITS -> tx.type == TransactionType.CREDIT
                        TransactionFilter.NEEDS_REVIEW -> !tx.isReviewed
                    }
                }
                .filter { tx ->
                    if (q.isBlank()) true
                    else {
                        val needle = q.trim().lowercase()
                        tx.merchant.lowercase().contains(needle) ||
                            tx.amount.toString().contains(needle) ||
                            tx.rawMessage.lowercase().contains(needle)
                    }
                }
                .map { tx ->
                    val cat = meta[tx.categoryId]
                        ?: Triple("Uncategorized", Icons.Default.SwapHoriz, Color.Gray)
                    TransactionUiModel(
                        id            = tx.id,
                        merchantName  = tx.merchant,
                        categoryName  = cat.first,
                        categoryIcon  = cat.second,
                        categoryColor = cat.third,
                        amount        = tx.amount,
                        dateTime      = tx.dateTime,
                        type          = tx.type,
                        categoryId    = tx.categoryId,
                        isReviewed    = tx.isReviewed,
                        isExcluded    = tx.isExcluded,
                        source        = tx.source.name,
                        rawMessage    = tx.rawMessage,
                        messageSource = tx.messageSource,
                    )
                }
                .let { seq ->
                    when (sort) {
                        SortOrder.NEWEST_FIRST -> seq.sortedByDescending { it.dateTime }
                        SortOrder.OLDEST_FIRST -> seq.sortedBy { it.dateTime }
                        SortOrder.AMOUNT_HIGH  -> seq.sortedByDescending { it.amount }
                        SortOrder.AMOUNT_LOW   -> seq.sortedBy { it.amount }
                    }
                }
                .toList()
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categoryItems: StateFlow<List<CategoryPickerItem>> =
        combine(
            transactionRepository.categories,
            transactionRepository.categoryMetaFlow,
        ) { cats, meta ->
            cats.filter { it.id != 0L }.mapNotNull { cat ->
                val (name, icon, color) = meta[cat.id] ?: return@mapNotNull null
                CategoryPickerItem(cat.id, name, icon, color)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(q: String) { _query.value = q }
    fun setFilter(f: TransactionFilter) { _filter.value = f }
    fun setPeriodFilter(p: PeriodFilter) { _periodFilter.value = p }
    fun setSortOrder(s: SortOrder) { _sortOrder.value = s }

    fun manualSync() {
        SyncWorker.scheduleManualSync(getApplication(), userPreferences.getSyncDays())
    }

    fun saveManualTransaction(
        amount: Double,
        merchant: String,
        categoryId: Long,
        dateTime: Long,
        type: TransactionType,
        note: String?,
    ) {
        viewModelScope.launchCatching(TAG) {
            transactionRepository.insertTransaction(
                Transaction(
                    id             = UUID.randomUUID().toString(),
                    amount         = amount,
                    currency       = "INR",
                    merchant       = merchant,
                    categoryId     = categoryId,
                    accountId      = null,
                    source         = TransactionSource.MANUAL,
                    dateTime       = dateTime,
                    type           = type,
                    isReviewed     = true,
                    isSplit        = false,
                    splitAmount    = null,
                    isReimbursement = false,
                    note           = note?.takeIf { it.isNotBlank() },
                    rawMessage     = "",
                    messageSource  = "",
                )
            )
        }
    }

    fun updateTransaction(
        id: String,
        amount: Double,
        merchant: String,
        categoryId: Long,
        dateTime: Long,
        type: TransactionType,
        note: String?,
    ) {
        viewModelScope.launchCatching(TAG) {
            val existing = transactionDao.getById(id) ?: return@launchCatching
            transactionDao.update(
                existing.copy(
                    amount      = amount,
                    merchant    = merchant,
                    categoryId  = categoryId,
                    dateTime    = dateTime,
                    type        = type,
                    note        = note?.takeIf { it.isNotBlank() },
                    isUserEdited = true,
                )
            )
        }
    }

    fun deleteTransaction(id: String) {
        viewModelScope.launchCatching(TAG) { transactionRepository.softDelete(id) }
    }

    fun markReviewed(id: String) {
        viewModelScope.launchCatching(TAG) { transactionRepository.markReviewed(id) }
    }

    private companion object {
        const val TAG = "TransactionsViewModel"
    }
}
