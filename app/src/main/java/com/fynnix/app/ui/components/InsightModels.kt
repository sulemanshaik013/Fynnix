package com.fynnix.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class InsightSeverity {
    POSITIVE, WARNING, NEUTRAL
}

data class InsightUiModel(
    val id: String,
    val text: String, // Short text for the card
    val icon: ImageVector,
    val iconColor: Color,
    val detailTitle: String,
    val detailDescription: String,
    val detailTag: String, // Metric chip text
    val detailValue: String, // Highlight value
    val severity: InsightSeverity,
    val actionText: String? = null,
    val onAction: (() -> Unit)? = null
)
