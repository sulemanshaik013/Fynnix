package com.fynnix.app.features.settings.ui

import androidx.lifecycle.ViewModel
import com.fynnix.app.core.data.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val prefs: UserPreferences,
) : ViewModel() {
    fun load(): Pair<String, Double> = prefs.getUserName() to prefs.getUserIncome()

    fun save(name: String, income: Double) {
        prefs.saveUserName(name.trim().ifBlank { "User" })
        prefs.saveUserIncome(income.coerceAtLeast(0.0))
    }
}
