package com.fynnix.app.features.home.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fynnix.app.core.ui.theme.CardCornerRadius
import com.fynnix.app.core.ui.theme.Primary
import com.fynnix.app.features.home.ui.UpcomingItem
import java.util.Calendar

@Composable
fun UpcomingSection(items: List<UpcomingItem>) {
    if (items.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text(
            "UPCOMING",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Card(
            shape = RoundedCornerShape(CardCornerRadius),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                items.forEachIndexed { index, item ->
                    UpcomingRow(item)
                    if (index != items.lastIndex) {
                        androidx.compose.material3.HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpcomingRow(item: UpcomingItem) {
    val daysAway = daysUntil(item.dueDate)
    val urgencyColor = when {
        daysAway <= 0 -> Color(0xFFEF4444)
        daysAway == 1 -> Color(0xFFF59E0B)
        else -> Primary
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                Icons.Default.Event, contentDescription = null,
                tint = urgencyColor, modifier = androidx.compose.ui.Modifier.padding(end = 12.dp),
            )
            Text(
                item.label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            if (item.amount != null) {
                Text(
                    "₹${"%,.0f".format(item.amount)}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                urgencyLabel(daysAway),
                style = MaterialTheme.typography.labelSmall,
                color = urgencyColor,
            )
        }
    }
}

private fun urgencyLabel(daysAway: Int): String = when {
    daysAway < 0 -> "Overdue"
    daysAway == 0 -> "Due today"
    daysAway == 1 -> "Due tomorrow"
    else -> "Due in $daysAway days"
}

private fun daysUntil(dueDate: Long): Int {
    val today = startOfDay(System.currentTimeMillis())
    val due = startOfDay(dueDate)
    return ((due - today) / (24L * 60 * 60 * 1000)).toInt()
}

private fun startOfDay(ts: Long): Long {
    val cal = Calendar.getInstance().apply { timeInMillis = ts }
    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}
