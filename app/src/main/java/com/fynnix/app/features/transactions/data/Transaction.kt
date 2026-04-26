package com.fynnix.app.features.transactions.data

import com.fynnix.app.core.utils.TransactionSource
import com.fynnix.app.core.utils.TransactionType

data class Transaction(
    val id: String,
    val amount: Double,
    val currency: String = "INR",
    val merchant: String,
    val categoryId: Long,
    val accountId: Long?,
    val source: TransactionSource,
    val dateTime: Long,
    val type: TransactionType,
    val isReviewed: Boolean,
    val isExcluded: Boolean = false,
    val isSplit: Boolean,
    val splitAmount: Double?,
    val isReimbursement: Boolean,
    val note: String?,
    val rawMessage: String = "",
    val messageSource: String = "",
)
