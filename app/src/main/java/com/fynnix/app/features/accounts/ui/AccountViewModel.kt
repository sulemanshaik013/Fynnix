package com.fynnix.app.features.accounts.ui

import com.fynnix.app.core.utils.AccountSource
import com.fynnix.app.core.utils.AccountType

data class AccountUiModel(
    val id: Long,
    val bankName: String,
    val type: String,
    val rawType: AccountType,
    val lastFourDigits: String?,
    val source: AccountSource,
    val balance: Double,
    val updatedAt: Long,
    val monthlySpend: Double = 0.0,
    val monthlyIncome: Double = 0.0,
)
