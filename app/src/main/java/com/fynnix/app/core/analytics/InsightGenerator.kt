package com.fynnix.app.core.analytics

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NightsStay
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

        val debits = monthTx.filter { it.type.name == "DEBIT" && !it.isExcluded }
        val totalSpend = debits.sumOf { it.amount }

        // 1. Cashflow ratio
        if (monthlyIncome > 0) {
            val ratio = (totalSpend / monthlyIncome * 100).toInt().coerceIn(0, 999)
            val severity = if (ratio < 70) InsightSeverity.POSITIVE else InsightSeverity.WARNING
            out += InsightUiModel(
                id = "cashflow",
                text = if (ratio < 70) "Healthy cashflow this month" else "High spend vs income",
                icon = if (severity == InsightSeverity.POSITIVE) Icons.Default.CheckCircle else Icons.Default.Warning,
                iconColor = if (severity == InsightSeverity.POSITIVE) Color(0xFF22C55E) else Color(0xFFEF4444),
                detailTitle = if (severity == InsightSeverity.POSITIVE)
                    "Cashflow looks healthy" else "Spending is high vs income",
                detailTag = "% of income spent",
                detailValue = "$ratio% of income spent",
                detailDescription = "You've spent ${ratio}% of your monthly income so far.",
                severity = severity,
            )
        }

        // 2. Top spending category
        if (debits.isNotEmpty()) {
            val byCat = debits.groupBy { it.categoryId }
                .mapValues { (_, list) -> list.sumOf { it.amount } }
                .maxByOrNull { it.value }
            if (byCat != null && byCat.value > 0) {
                val name = categoryNames[byCat.key] ?: "Uncategorized"
                val pct = if (totalSpend > 0) (byCat.value / totalSpend * 100).toInt() else 0
                out += InsightUiModel(
                    id = "top-cat",
                    text = "$name dominates this month",
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    iconColor = Color(0xFFF59E0B),
                    detailTitle = "$name dominates this month",
                    detailTag = "Top category",
                    detailValue = "₹${byCat.value.toInt()} ($pct%)",
                    detailDescription = "$name is ${pct}% of your spend this month.",
                    severity = InsightSeverity.NEUTRAL,
                )
            }
        }

        // 3. Late-night spending
        val lateNight = debits.count { isLateNight(it.dateTime) }
        if (lateNight >= 3) {
            out += InsightUiModel(
                id = "late-night",
                text = "Late-night spending pattern",
                icon = Icons.Default.NightsStay,
                iconColor = Color(0xFF818CF8),
                detailTitle = "Late-night spending",
                detailTag = "Behaviour",
                detailValue = "$lateNight transactions after 11 PM",
                detailDescription = "We noticed $lateNight transactions between 11 PM and 5 AM this month.",
                severity = InsightSeverity.NEUTRAL,
            )
        }

        // 4. Subscriptions detected
        if (subscriptionCount > 0) {
            out += InsightUiModel(
                id = "subs",
                text = "$subscriptionCount active subscriptions detected",
                icon = Icons.Default.Info,
                iconColor = Color(0xFF60A5FA),
                detailTitle = "$subscriptionCount active subscriptions",
                detailTag = "Recurring spend",
                detailValue = "₹${subscriptionMonthlyTotal.toInt()} monthly",
                detailDescription = "We've detected $subscriptionCount recurring payments. Review them in the Subscriptions tab.",
                severity = InsightSeverity.NEUTRAL,
            )
        }

        return out
    }

    private fun isLateNight(ts: Long): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = ts }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return hour >= 23 || hour < 5
    }
}
