package com.fynnix.app.features.transactions.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fynnix.app.core.ui.theme.Primary
import com.fynnix.app.core.utils.TransactionType
import com.fynnix.app.features.transactions.ui.TransactionUiModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailSheet(
    transaction: TransactionUiModel,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSplitReimbursementChange: (isSplit: Boolean, splitAmount: Double?, isReimbursement: Boolean) -> Unit = { _, _, _ -> },
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(transaction.categoryColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = transaction.categoryIcon,
                        contentDescription = null,
                        tint = transaction.categoryColor,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = transaction.merchantName,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = transaction.categoryName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "AMOUNT",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            Text(
                text = "₹${"%,d".format(transaction.amount.toInt())}",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (transaction.type == TransactionType.DEBIT) Color.White else Color(0xFF22C55E)
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            SplitReimbursementSection(
                amount = transaction.amount,
                isSplit = transaction.isSplit,
                splitAmount = transaction.splitAmount,
                isReimbursement = transaction.isReimbursement,
                onChange = onSplitReimbursementChange,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                DetailItem(
                    label = "DATE",
                    value = formatFullDate(transaction.dateTime),
                    modifier = Modifier.weight(1f)
                )
                DetailItem(
                    label = "TYPE",
                    value = if (transaction.type == TransactionType.DEBIT) "Debit" else "Credit",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                DetailItem(
                    label = "SOURCE",
                    value = transaction.source,
                    modifier = Modifier.weight(1f)
                )
                if (transaction.messageSource.isNotEmpty()) {
                    DetailItem(
                        label = "SENDER",
                        value = transaction.messageSource,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (transaction.rawMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "ORIGINAL SMS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = Color.White.copy(alpha = 0.04f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = transaction.rawMessage,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = Color(0xFFE0E0E0)
                        ),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete")
                }
                Button(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Edit", color = Color.Black)
                }
            }
        }
    }
}

/**
 * "Split" and "Reimbursable" are independent flags — a split expense (your share of a
 * group bill) can also be one you expect reimbursed, so both toggles can be on at once
 * rather than being mutually exclusive.
 */
@Composable
private fun SplitReimbursementSection(
    amount: Double,
    isSplit: Boolean,
    splitAmount: Double?,
    isReimbursement: Boolean,
    onChange: (isSplit: Boolean, splitAmount: Double?, isReimbursement: Boolean) -> Unit,
) {
    var splitAmountText by remember(isSplit) {
        mutableStateOf(splitAmount?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() } ?: "")
    }

    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = isSplit,
                onClick = { onChange(!isSplit, splitAmount, isReimbursement) },
                label = { Text("Split") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.CallSplit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary, selectedLabelColor = Color.Black, selectedLeadingIconColor = Color.Black),
            )
            FilterChip(
                selected = isReimbursement,
                onClick = { onChange(isSplit, splitAmount, !isReimbursement) },
                label = { Text("Reimbursable") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(16.dp)) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary, selectedLabelColor = Color.Black, selectedLeadingIconColor = Color.Black),
            )
        }
        if (isSplit) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "YOUR SHARE OF ₹${"%,.0f".format(amount)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp,
            )
            TextField(
                value = splitAmountText,
                onValueChange = { v ->
                    if (v.all { it.isDigit() || it == '.' } && v.count { it == '.' } <= 1) {
                        splitAmountText = v
                        onChange(true, v.toDoubleOrNull(), isReimbursement)
                    }
                },
                placeholder = { Text("e.g. ${(amount / 2).let { if (it == it.toLong().toDouble()) it.toLong().toString() else "%.2f".format(it) }}", color = Color.Gray) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Gray.copy(alpha = 0.5f),
                    focusedIndicatorColor = Primary,
                    cursorColor = Primary,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                ),
            )
        }
    }
}

@Composable
private fun DetailItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatFullDate(ts: Long): String {
    if (ts <= 0) return "—"
    val sdf = SimpleDateFormat("EEE, d MMM yyyy • HH:mm", Locale.getDefault())
    return sdf.format(Date(ts))
}
