package com.fynnix.app.features.transactions.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.fynnix.app.core.utils.TransactionType

data class TransactionUiModel(
    val id: String,
    val merchantName: String,
    val categoryName: String,
    val categoryIcon: ImageVector,
    val categoryColor: Color,
    val amount: Double,
    val dateTime: Long,
    val type: TransactionType,
    val categoryId: Long = 0L,
    val isReviewed: Boolean = true,
    val isExcluded: Boolean = false,
    val source: String = "Manual",
    val rawMessage: String = "",
    val messageSource: String = "",
    val isP2P: Boolean = false,
)

data class CategoryPickerItem(
    val id: Long,
    val name: String,
    val icon: ImageVector,
    val color: Color,
)
