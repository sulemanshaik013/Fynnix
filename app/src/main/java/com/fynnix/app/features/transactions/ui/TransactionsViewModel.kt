package com.fynnix.app.features.transactions.ui

import android.app.Application
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fynnix.app.core.data.UserPreferences
import com.fynnix.app.core.sms.SyncWorker
import com.fynnix.app.core.utils.TransactionType
import com.fynnix.app.features.transactions.data.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class TransactionFilter(val label: String) {
    ALL("All"),
    DEBITS("Debits"),
    CREDITS("Credits"),
    NEEDS_REVIEW("Needs review"),
}

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    application: Application,
    private val transactionRepository: TransactionRepository,
    private val userPreferences: UserPreferences,
) : AndroidViewModel(application) {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _filter = MutableStateFlow(TransactionFilter.ALL)
    val filter: StateFlow<TransactionFilter> = _filter

    val transactions: StateFlow<List<TransactionUiModel>> =
        combine(
            transactionRepository.getAllTransactions(),
            transactionRepository.categoryMetaFlow,
            _query,
            _filter,
        ) { list, meta, q, f ->
            list
                .asSequence()
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
                        id = tx.id,
                        merchantName = tx.merchant,
                        categoryName = cat.first,
                        categoryIcon = cat.second,
                        categoryColor = cat.third,
                        amount = tx.amount,
                        dateTime = tx.dateTime,
                        type = tx.type,
                        isReviewed = tx.isReviewed,
                        source = tx.source.name,
                        rawMessage = tx.rawMessage,
                        messageSource = tx.messageSource,
                    )
                }
                .toList()
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setQuery(q: String) { _query.value = q }
    fun setFilter(f: TransactionFilter) { _filter.value = f }

    fun manualSync() {
        SyncWorker.scheduleManualSync(getApplication(), userPreferences.getSyncDays())
    }
}
