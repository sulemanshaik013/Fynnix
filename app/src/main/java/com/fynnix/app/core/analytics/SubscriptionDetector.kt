package com.fynnix.app.core.analytics

import com.fynnix.app.features.subscriptions.ui.SubscriptionHistory
import com.fynnix.app.features.subscriptions.ui.SubscriptionUiModel
import com.fynnix.app.features.transactions.data.TransactionDao
import com.fynnix.app.features.transactions.data.TransactionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Detects recurring payments. Heuristic: ≥ 3 transactions to the same merchant
 * with intervals roughly 25–35 days and amounts within ±15%. Confidence is
 * higher when intervals tighten around 30 days and amounts cluster more
 * closely.
 */
@Singleton
class SubscriptionDetector @Inject constructor(
    private val dao: TransactionDao,
) {
    suspend fun detect(): List<SubscriptionUiModel> {
        val candidates = dao.recurringCandidatesFlow()
        // We use a flow query for liveness, but here in suspend we collect once.
        // The caller is a ViewModel that re-collects on dao changes.
        return emptyList() // never used directly — see flowDetect()
    }

    /** Build subscription list from a snapshot of recurring candidate merchants. */
    suspend fun buildFromCandidates(merchants: List<String>): List<SubscriptionUiModel> =
        merchants.mapNotNull { merchant ->
            val tx = dao.txByMerchant(merchant)
            if (tx.size < 3) return@mapNotNull null
            buildSubscription(merchant, tx)
        }

    private fun buildSubscription(
        merchant: String,
        tx: List<TransactionEntity>,
    ): SubscriptionUiModel? {
        val sorted = tx.sortedBy { it.dateTime }

        // Validate interval consistency.
        val intervalsDays = sorted.zipWithNext { a, b ->
            (b.dateTime - a.dateTime) / DAY_MILLIS.toDouble()
        }
        if (intervalsDays.isEmpty()) return null
        val avgInterval = intervalsDays.average()
        if (avgInterval < 25 || avgInterval > 35) return null

        // Validate amount consistency (±15% from median).
        val sortedAmounts = sorted.map { it.amount }.sorted()
        val median = sortedAmounts[sortedAmounts.size / 2]
        val withinTolerance = sortedAmounts.all { abs(it - median) <= median * 0.15 }
        if (!withinTolerance) return null

        val intervalScore = 1.0 - (abs(30.0 - avgInterval) / 5.0).coerceIn(0.0, 1.0) * 0.4
        val amountVariance = sortedAmounts.map { abs(it - median) / median }.average()
        val amountScore = 1.0 - amountVariance.coerceIn(0.0, 0.15) / 0.15 * 0.3
        val confidence = (intervalScore * amountScore).toFloat().coerceIn(0.5f, 0.99f)

        val last = sorted.last()
        val nextDue = last.dateTime + (avgInterval * DAY_MILLIS).toLong()

        return SubscriptionUiModel(
            id = UUID.nameUUIDFromBytes(merchant.toByteArray()).toString(),
            name = merchant,
            amount = median,
            nextBillingDate = DATE_FORMAT.format(Date(nextDue)),
            color = androidx.compose.ui.graphics.Color(0xFFE5B342),
            streakMonths = sorted.size,
            confidence = confidence,
            history = sorted.takeLast(6).map {
                SubscriptionHistory(
                    date = SHORT_DATE.format(Date(it.dateTime)),
                    amount = it.amount,
                )
            }
        )
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
        val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val SHORT_DATE = SimpleDateFormat("d MMM", Locale.US)
    }
}
