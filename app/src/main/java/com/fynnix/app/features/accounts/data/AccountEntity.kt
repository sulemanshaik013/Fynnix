package com.fynnix.app.features.accounts.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fynnix.app.core.utils.AccountType

@Entity(
    tableName = "accounts",
    indices = [Index(value = ["bankName", "lastFourDigits", "type"], unique = true)]
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: AccountType,
    val bankName: String,
    val lastFourDigits: String,
    val balance: Double = 0.0,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)
