package com.fynnix.app.features.subscriptions.ui

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fynnix.app.core.analytics.SubscriptionDetector
import com.fynnix.app.features.transactions.data.TransactionDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class SubscriptionUiModel(
    val id: String,
    val name: String,
    val amount: Double,
    val nextBillingDate: String,
    val color: Color,
    val streakMonths: Int,
    val confidence: Float, // 0.0 to 1.0
    val history: List<SubscriptionHistory> = emptyList()
)

data class SubscriptionHistory(
    val date: String,
    val amount: Double
)

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val dao: TransactionDao,
    private val detector: SubscriptionDetector,
) : ViewModel() {

    val subscriptions = dao.recurringCandidatesFlow()
        .map { rows -> detector.buildFromCandidates(rows.map { it.merchant }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
