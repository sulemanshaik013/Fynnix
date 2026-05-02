package com.fynnix.app.features.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fynnix.app.core.utils.launchCatching
import com.fynnix.app.features.transactions.data.FailedTransactionDao
import com.fynnix.app.features.transactions.data.FailedTransactionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class FailedSmsReviewViewModel @Inject constructor(
    private val failedTransactionDao: FailedTransactionDao,
) : ViewModel() {

    val failedList = failedTransactionDao.getAllFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(item: FailedTransactionEntity) {
        viewModelScope.launchCatching(TAG) { failedTransactionDao.delete(item) }
    }

    fun clearAll() {
        viewModelScope.launchCatching(TAG) { failedTransactionDao.clearAll() }
    }

    private companion object {
        const val TAG = "FailedSmsReviewVM"
    }
}
