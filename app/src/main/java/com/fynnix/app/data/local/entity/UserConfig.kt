package com.fynnix.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_config")
data class UserConfig(
    @PrimaryKey
    val id: Long = 1,
    val name: String,
    val monthlyIncome: Double,
    val hasCompletedOnboarding: Boolean = false,
    val hasGrantedSmsPermission: Boolean = false,
    val hasGivenPrivacyConsent: Boolean = false,
    val pinHash: String? = null,
    val isBiometricEnabled: Boolean = false,
    val appLockEnabled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)