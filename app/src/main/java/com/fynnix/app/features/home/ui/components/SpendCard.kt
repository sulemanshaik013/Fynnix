package com.fynnix.app.features.home.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fynnix.app.core.ui.components.FynnixProgressBar
import com.fynnix.app.core.ui.theme.CardCornerRadius
import com.fynnix.app.core.ui.theme.Primary
import com.fynnix.app.core.ui.utils.ColorUtils
import com.fynnix.app.features.insights.ui.SpendCardData
import java.util.*


@Composable
fun SpendCardPager(
    cards: List<SpendCardData>
) {
    val pagerState = rememberPagerState(pageCount = { cards.size })

    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 20.dp),
            pageSpacing = 12.dp
        ) { page ->
            SpendCard(data = cards[page])
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Pager Indicator
        Row(
            Modifier
                .height(8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(cards.size) { iteration ->
                val color = if (pagerState.currentPage == iteration) Primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .clip(CircleShape)
                        .background(color)
                        .size(8.dp)
                )
            }
        }
    }
}

@Composable
fun SpendCard(data: SpendCardData) {
    // Guard against both an absent income and a genuine 0.0 (the pre-onboarding default
    // from UserPreferences.getUserIncome()) — either would otherwise divide by zero and
    // produce NaN/Infinity, which coerceIn passes through unchanged since NaN comparisons
    // are always false.
    val income = data.incomeAmount?.takeIf { it > 0.0 } ?: 1.0
    val percentage = (data.debitAmount / income).toFloat().coerceIn(0f, 1f)
    val progressColor = ColorUtils.getProgressColor(percentage)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CardCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = data.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "₹${formatCurrency(data.debitAmount)}",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Primary,
                    fontSize = 36.sp
                )
            )

            if (data.creditAmount > 0) {
                Text(
                    text = "+ ₹${formatCurrency(data.creditAmount)} incoming",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF22C55E) // Success Green
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            
            if (data.incomeAmount != null) {
                FynnixProgressBar(
                    progress = percentage,
                    color = progressColor
                )

                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${(percentage * 100).toInt()}% of income spent",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "₹${formatCurrency((data.incomeAmount - data.debitAmount).coerceAtLeast(0.0))} left",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Last synced: ${data.lastSynced}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

private fun formatCurrency(amount: Double): String {
    return String.format(Locale.US, "%,.0f", amount)
}
