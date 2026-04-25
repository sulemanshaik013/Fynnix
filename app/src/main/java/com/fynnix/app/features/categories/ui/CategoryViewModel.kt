package com.fynnix.app.features.categories.ui


import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class CategoryBudgetUiModel(
    val name: String,
    val icon: ImageVector,
    val color: Color,
    val budgetLimit: Double
)

data class CategorySpend(
    val categoryName: String,
    val amount: Double,
    val percentage: Float,
    val color: Color
)

class CategoryViewModel {
}