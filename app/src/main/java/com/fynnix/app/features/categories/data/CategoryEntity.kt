package com.fynnix.app.features.categories.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a spending category.
 *
 * Budget amounts are intentionally NOT stored here — they live in [BudgetEntity]
 * which supports period types (monthly/yearly) and tracks history over time.
 * Keeping budget in a single place prevents the stale-data divergence that
 * existed when both CategoryEntity.budget and BudgetEntity.amount were in use.
 */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val icon: String,
    val colorHex: String,
    val isDefault: Boolean,
    val sortOrder: Int,
)
