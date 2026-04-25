package com.fynnix.app.features.onboarding.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fynnix.app.core.data.UserPreferences
import com.fynnix.app.core.sms.ProcessingProgress
import com.fynnix.app.core.sms.SmsProcessingManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AllSetViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
) : ViewModel() {

    val processingProgress: Flow<ProcessingProgress> =
        SmsProcessingManager.processingProgress

    fun completeOnboarding() {
        viewModelScope.launch {
            userPreferences.setOnboardingCompleted(true)
        }
    }
}