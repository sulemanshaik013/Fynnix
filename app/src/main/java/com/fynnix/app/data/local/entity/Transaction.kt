package com.fynnix.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Account::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("categoryId"), Index("accountId")]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val merchantName: String,
    val categoryId: Long,
    val accountId: Long?,
    val source: TransactionSource,
    val dateTime: Long,
    val transactionType: TransactionType,
    val paymentMode: PaymentMode?,
    val note: String?,
    val rawSms: String?,
    val smsHash: Int?,
    val isReviewed: Boolean = false,
    val isSplit: Boolean = false,
    val splitAmount: Double?,
    val isReimbursement: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)