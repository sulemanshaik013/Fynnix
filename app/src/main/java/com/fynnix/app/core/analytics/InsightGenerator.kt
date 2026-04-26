package com.fynnix.app.core.analytics

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Color
import com.fynnix.app.features.insights.ui.InsightSeverity
import com.fynnix.app.features.insights.ui.InsightUiModel
import com.fynnix.app.features.transactions.data.TransactionEntity
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pure function: takes a snapshot of transactions + categories and returns a
 * list of human-readable insights. No DB access — analytics layer is
 * deterministic and testable.
 */
@Singleton
class InsightGenerator @Inject constructor() {

    fun generate(
        monthTx: List<TransactionEntity>,
        categoryNames: Map<Long, String>,
        monthlyIncome: Double,
        subscriptionCount: Int,
        subscriptionMonthlyTotal: Double,
    ): List<InsightUiModel> {
        val out = mutableListOf<InsightUiModel>()

        val debits     = monthTx.filter { it.type.name == "DEBIT"  && !it.isExcluded }
        val credits    = monthTx.filter { it.type.name == "CREDIT" && !it.isExcluded }
        val totalSpend = debits.sumOf { it.amount }
        val totalIn    = credits.sumOf { it.amount }

        // 1. Cashflow ratio
        if (monthlyIncome > 0) {
            val ratio = (totalSpend / monthlyIncome * 100).toInt().coerceIn(0, 999)
            val isHealthy = ratio < 70
            out += InsightUiModel(
                id          = "cashflow",
                text        = if (isHealthy) "Healthy cashflow — $ratio% of income spent" else "Spending at $ratio% of income",
                icon        = if (isHealthy) Icons.Default.CheckCircle else Icons.Default.Warning,
                iconColor   = if (isHealthy) Color(0xFF22C55E) else Color(0xFFEF4444),
                detailTitle = if (isHealthy) "Cashflow looks healthy" else "Spending is high vs income",
                detailTag   = "Cashflow",
                detailValue = "$ratio% of income spent",
                detailDescription = "You've spent ₹${totalSpend.toInt()} out of your ₹${monthlyIncome.toInt()} monthly income (${ratio}%).",
                severity    = if (isHealthy) InsightSeverity.POSITIVE else InsightSeverity.WARNING,
            )
        }

        // 2. Top spending category
        if (debits.isNotEmpty()) {
            val byCat = debits.groupBy { it.categoryId }
                .mapValues { (_, list) -> list.sumOf { it.amount } }
                .maxByOrNull { it.value }
            if (byCat != null && byCat.value > 0) {
                val name = categoryNames[byCat.key] ?: "Uncategorized"
                val pct  = if (totalSpend > 0) (byCat.value / totalSpend * 100).toInt() else 0
                out += InsightUiModel(
                    id          = "top-cat",
                    text        = "$name is your top category ($pct% of spend)",
                    icon        = Icons.AutoMirrored.Filled.TrendingUp,
                    iconColor   = Color(0xFFF59E0B),
                    detailTitle = "$name dominates this month",
                    detailTag   = "Top category",
                    detailValue = "₹${byCat.value.toInt()} ($pct%)",
                    detailDescription = "$name accounts for $pct% of your total spend this month.",
                    severity    = InsightSeverity.NEUTRAL,
                )
            }
        }

        // 3. Top merchant frequency
        if (debits.isNotEmpty()) {
            val topMerchant = debits
                .filter { it.merchant.isNotBlank() }
                .groupBy { it.merchant }
                .maxByOrNull { it.value.size }
            if (topMerchant != null && topMerchant.value.size >= 3) {
                val count  = topMerchant.value.size
                val amount = topMerchant.value.sumOf { it.amount }
                out += InsightUiModel(
                    id          = "top-merchant",
                    text        = "You visited ${topMerchant.key} $count times this month",
                    icon        = Icons.Default.ShoppingCart,
                    iconColor   = Color(0xFF60A5FA),
                    detailTitle = "${topMerchant.key} — top merchant",
                    detailTag   = "Frequency",
                    detailValue = "$count transactions · ₹${amount.toInt()} total",
                    detailDescription = "Your most frequent merchant this month, totalling ₹${amount.toInt()}.",
                    severity    = InsightSeverity.NEUTRAL,
                )
            }
        }

        // 4. Weekend vs weekday spending
        if (debits.size >= 5) {
            val weekendSpend  = debits.filter { isWeekend(it.dateTime) }.sumOf { it.amount }
            val weekdaySpend  = debits.filter { !isWeekend(it.dateTime) }.sumOf { it.amount }
            if (weekendSpend > 0 && weekdaySpend > 0) {
                val weekendPct = (weekendSpend / totalSpend * 100).toInt()
                if (weekendPct > 40) {
                    out += InsightUiModel(
                        id          = "weekend-spend",
                        text        = "${weekendPct}% of your spending happens on weekends",
                        icon        = Icons.Default.Star,
                        iconColor   = Color(0xFFA78BFA),
                        detailTitle = "Weekend spending pattern",
                        detailTag   = "Behaviour",
                        detailValue = "₹${weekendSpend.toInt()} on weekends",
                        detailDescription = "You spend $weekendPct% of your monthly total on weekends. Weekdays: ₹${weekdaySpend.toInt()}.",
                        severity    = InsightSeverity.NEUTRAL,
                    )
                }
            }
        }

        // 5. Late-night spending
        val lateNight = debits.count { isLateNight(it.dateTime) }
        if (lateNight >= 3) {
            out += InsightUiModel(
                id          = "late-night",
                text        = "$lateNight late-night transactions this month",
                icon        = Icons.Default.NightsStay,
                iconColor   = Color(0xFF818CF8),
                detailTitle = "Late-night spending",
                detailTag   = "Behaviour",
                detailValue = "$lateNight transactions after 11 PM",
                detailDescription = "We noticed $lateNight transactions between 11 PM and 5 AM this month.",
                severity    = InsightSeverity.NEUTRAL,
            )
        }

        // 6. Subscriptions detected
        if (subscriptionCount > 0) {
            out += InsightUiModel(
                id          = "subs",
                text        = "$subscriptionCount recurring payments · ₹${subscriptionMonthlyTotal.toInt()}/mo",
                icon        = Icons.Default.Info,
                iconColor   = Color(0xFF60A5FA),
                detailTitle = "$subscriptionCount active subscriptions",
                detailTag   = "Recurring spend",
                detailValue = "₹${subscriptionMonthlyTotal.toInt()} monthly",
                detailDescription = "We've detected $subscriptionCount recurring payments. Review them in the Subscriptions tab.",
                severity    = InsightSeverity.NEUTRAL,
            )
        }

        // 7. Single large transaction flag
        if (debits.isNotEmpty()) {
            val largest = debits.maxByOrNull { it.amount }
            if (largest != null && totalSpend > 0 && largest.amount / totalSpend > 0.30) {
                val pct = (largest.amount / totalSpend * 100).toInt()
                out += InsightUiModel(
                    id          = "large-tx",
                    text        = "One transaction is $pct% of your total spend",
                    icon        = Icons.Default.Warning,
                    iconColor   = Color(0xFFF59E0B),
                    detailTitle = "Large single transaction",
                    detailTag   = "Unusual",
                    detailValue = "₹${largest.amount.toInt()} at ${largest.merchant}",
                    detailDescription = "${largest.merchant} — ₹${largest.amount.toInt()} is $pct% of your total monthly spend.",
                    severity    = InsightSeverity.WARNING,
                )
            }
        }

        return out
    }

    private fun isLateNight(ts: Long): Boolean {
        val hour = Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.HOUR_OF_DAY)
        return hour >= 23 || hour < 5
    }

    private fun isWeekend(ts: Long): Boolean {
        val day = Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.DAY_OF_WEEK)
        return day == Calendar.SATURDAY || day == Calendar.SUNDAY
    }
}
