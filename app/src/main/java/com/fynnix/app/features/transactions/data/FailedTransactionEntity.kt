package com.fynnix.app.features.transactions.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "failed_transactions")
data class FailedTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val rawSMS: String,
    val reason: String,
    val timestamp: Long,
    val retryCount: Int = 0,
    val lastAttempt: Long = System.currentTimeMillis()
)