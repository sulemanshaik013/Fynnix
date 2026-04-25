package com.fynnix.app.features.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fynnix.app.features.transactions.data.FailedTransactionDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    failedTransactionDao: FailedTransactionDao,
) : ViewModel() {

    /** Live count of SMS messages that looked financial but couldn't be parsed.
     *  Shown as a badge on the Data Management row so users know to review them. */
    val failedSmsCount = failedTransactionDao.countFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
}
