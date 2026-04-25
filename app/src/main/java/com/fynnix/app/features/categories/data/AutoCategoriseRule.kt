package com.fynnix.app.features.categories.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fynnix.app.core.utils.RuleCondition
import com.fynnix.app.core.utils.RuleField

@Entity(
    tableName = "auto_categorise_rules",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("categoryId")]
)
data class AutoCategoriseRule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val ruleName: String,
    val field: RuleField,
    val condition: RuleCondition,
    val value: String,
    val categoryId: Long,
    val priority: Int,
    val createdAt: Long = System.currentTimeMillis()
)