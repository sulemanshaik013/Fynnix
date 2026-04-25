package com.fynnix.app.features.categories.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "merchant_category_overrides",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["merchantKey"], unique = true),
        Index("categoryId"),
    ]
)
data class MerchantCategoryOverride(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val merchantKey: String,
    val categoryId: Long,
    val createdAt: Long = System.currentTimeMillis(),
    /** Updated whenever the user re-maps this merchant to a different category. */
    val updatedAt: Long = System.currentTimeMillis(),
)
