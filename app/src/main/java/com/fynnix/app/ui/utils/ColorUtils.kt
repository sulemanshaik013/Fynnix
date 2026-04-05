package com.fynnix.app.ui.utils

import androidx.compose.ui.graphics.Color
import com.fynnix.app.ui.theme.Success
import com.fynnix.app.ui.theme.Error
import com.fynnix.app.ui.theme.Primary

object ColorUtils {
    fun getProgressColor(percentage: Float): Color {
        return when {
            percentage < 0.7f -> Success
            percentage < 0.9f -> Primary
            else -> Error
        }
    }
}