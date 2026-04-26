package com.fynnix.app.features.transactions.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fynnix.app.core.utils.TransactionSource
import com.fynnix.app.core.utils.TransactionType
import com.fynnix.app.features.accounts.data.AccountEntity
import com.fynnix.app.features.categories.data.CategoryEntity

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["dateTime"]),
        Index(value = ["categoryId"]),
        Index(value = ["accountId"]),
        Index(value = ["smsHash"], unique = true),
        // Unique so a TOCTOU race between concurrent sync paths (periodic + incremental
        // SMS-triggered work can run at the same time) can never insert two rows for the
        // same real transaction — SQLite enforces this atomically regardless of app-level
        // check-then-insert races. SQLite treats each NULL as distinct, so manually-entered
        // transactions (which have no dedupKey) never collide with each other.
        Index(value = ["dedupKey"], unique = true),
    ],
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            // Falls back to the seeded "Uncategorized" category (id 0) rather than leaving
            // a dangling categoryId when the user deletes a custom category that still has
            // transactions tagged with it.
            onDelete = ForeignKey.SET_DEFAULT,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class TransactionEntity(
    @PrimaryKey
    val id: String,
    val amount: Double,
    val currency: String = "INR",
    val type: TransactionType,
    val merchant: String,
    // SQL-level default is required (not just the Kotlin default) so ForeignKey.SET_DEFAULT
    // has a value to fall back to when the referenced category row is deleted.
    @ColumnInfo(defaultValue = "0")
    val categoryId: Long = 0L,
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
