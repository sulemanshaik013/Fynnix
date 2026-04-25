package com.fynnix.app.features.accounts.ui

import com.fynnix.app.core.utils.AccountSource
import com.fynnix.app.core.utils.AccountType

data class AccountUiModel(
    val id: Long,
    val bankName: String,
    val type: String,            // display label
    val rawType: AccountType,
    val lastFourDigits: String?,
    val source: AccountSource,
    val balance: Double,
    val updatedAt: Long,
)
