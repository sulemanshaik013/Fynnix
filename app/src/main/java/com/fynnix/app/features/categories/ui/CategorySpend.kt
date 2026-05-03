package com.fynnix.app.features.categories.ui

import androidx.compose.ui.graphics.Color

data class CategorySpend(
    val categoryName: String,
    val amount: Double,
    val percentage: Float,
    val color: Color
)
