package com.fynnix.app.features.transactions.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fynnix.app.core.utils.TransactionType
import com.fynnix.app.features.transactions.ui.TransactionUiModel
import com.fynnix.app.core.ui.theme.Primary

@Composable
fun TransactionListItem(
    transaction: TransactionUiModel,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Merchant Icon (Category based)
        Box(
            modifier = Modifier.size(48.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = transaction.categoryColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = transaction.categoryIcon,
                    contentDescription = null,
                    tint = transaction.categoryColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // Yellow dot badge for unreviewed transactions
            if (!transaction.isReviewed) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp)
                        .size(10.dp)
                        .background(Primary, CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Merchant Info
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = transaction.merchantName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    color = Color.White
                )
                
                if (transaction.isP2P) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Gray.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "P2P",
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.sp,
                            color = Color.LightGray
                        )
                    }
                }
            }
            Text(
                text = transaction.source,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }

        // Right side: Amount and Category Chip
        Column(horizontalAlignment = Alignment.End) {
            val amountColor = if (transaction.type == TransactionType.DEBIT) Color(0xFFEF4444) else Color(0xFF22C55E)
            Text(
                text = "₹${formatTransactionAmount(transaction.amount)}",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = amountColor
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = transaction.categoryColor.copy(alpha = 0.15f)
            ) {
                Text(
                    text = transaction.categoryName.uppercase(),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    fontSize = 9.sp,
                    color = transaction.categoryColor
                )
            }
        }
    }
}

private fun formatTransactionAmount(amount: Double): String {
    val formatter = java.text.DecimalFormat("#,###")
    return formatter.format(amount)
}
