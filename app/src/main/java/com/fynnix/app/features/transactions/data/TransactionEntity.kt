package com.fynnix.app.features.transactions.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fynnix.app.core.utils.TransactionSource
import com.fynnix.app.core.utils.TransactionType

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["dateTime"]),
        Index(value = ["categoryId"]),
        Index(value = ["smsHash"], unique = true),
        Index(value = ["dedupKey"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey
    val id: String,
    val amount: Double,
    val currency: String = "INR",
    val type: TransactionType,
    val merchant: String,
    val categoryId: Long,
    val accountId: Long? = null,
    val dateTime: Long,
    val source: TransactionSource,
    val smsReference: String = "",
    val rawMessage: String = "",
    val messageSource: String = "",
    val parsingConfidence: Float = 1.0f,
    /** SHA-256 hex of (sender + "|" + body) — exact-SMS dedup. */
    val smsHash: String? = null,
    /** Coarse cross-channel dedup key — `(amount, dayBucket, normalizedMerchant)`. */
    val dedupKey: String? = null,
    val isReviewed: Boolean = false,
    val isDuplicate: Boolean = false,
    val isExcluded: Boolean = false,
    val isSplit: Boolean = false,
    val splitAmount: Double? = null,
    val isReimbursement: Boolean = false,
    val isUserEdited: Boolean = false,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val syncedAt: Long = System.currentTimeMillis(),
    /** Epoch ms when the user soft-deleted this row. NULL means not deleted. */
    val deletedAt: Long? = null,
)
