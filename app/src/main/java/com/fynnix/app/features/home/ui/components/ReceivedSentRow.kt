package com.fynnix.app.features.home.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * This month's received/sent totals as plain numbers with a directional arrow — no
 * "Received"/"Sent" labels, since the arrow + color already carries that meaning.
 */
@Composable
fun ReceivedSentRow(received: Double, sent: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        AmountChip(icon = Icons.Default.ArrowDownward, amount = received, color = Color(0xFF22C55E))
        AmountChip(icon = Icons.Default.ArrowUpward, amount = sent, color = Color(0xFFEF4444))
    }
}

@Composable
private fun AmountChip(icon: androidx.compose.ui.graphics.vector.ImageVector, amount: Double, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.width(16.dp))
        Text(
            text = "₹${String.format(Locale.US, "%,.0f", amount)}",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = color,
        )
    }
}
