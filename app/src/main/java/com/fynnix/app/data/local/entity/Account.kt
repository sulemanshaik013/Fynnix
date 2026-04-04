package com.fynnix.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: AccountType,
    val bankName: String,
    val lastFourDigits: String,
    val isActive: Boolean,
    val createdAt: Long = System.currentTimeMillis()
)
