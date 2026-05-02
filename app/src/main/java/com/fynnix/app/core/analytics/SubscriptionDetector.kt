package com.fynnix.app.core.analytics

import com.fynnix.app.core.utils.AppConstants
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
    /** Build subscription list from a snapshot of recurring candidate merchants. */
    suspend fun buildFromCandidates(merchants: List<String>): List<SubscriptionUiModel> =
        merchants.mapNotNull { merchant ->
            val tx = dao.txByMerchant(merchant)
            if (tx.size < AppConstants.SUBSCRIPTION_MIN_OCCURRENCES) return@mapNotNull null
            buildSubscription(merchant, tx)
        }

    private fun buildSubscription(
        merchant: String,
        tx: List<TransactionEntity>,
    ): SubscriptionUiModel? {
        val sorted = tx.sortedBy { it.dateTime }

        // Validate interval consistency. Checking only the average lets wildly uneven
        // gaps average out to something in-range (e.g. 10 and 50 days average to a
        // plausible 30) — every individual gap must fall in the accepted band too.
        val intervalsDays = sorted.zipWithNext { a, b ->
            (b.dateTime - a.dateTime) / DAY_MILLIS.toDouble()
        }
        if (intervalsDays.isEmpty()) return null
        val minDays = AppConstants.SUBSCRIPTION_MIN_INTERVAL_DAYS
        val maxDays = AppConstants.SUBSCRIPTION_MAX_INTERVAL_DAYS
        if (intervalsDays.any { it < minDays || it > maxDays }) return null
        val avgInterval = intervalsDays.average()

        // Validate amount consistency (±variance from median).
        val variance = AppConstants.SUBSCRIPTION_AMOUNT_VARIANCE
        val sortedAmounts = sorted.map { it.amount }.sorted()
        val median = sortedAmounts[sortedAmounts.size / 2]
        val withinTolerance = sortedAmounts.all { abs(it - median) <= median * variance }
        if (!withinTolerance) return null

        val intervalScore = 1.0 - (abs(30.0 - avgInterval) / 5.0).coerceIn(0.0, 1.0) * 0.4
        val amountVariance = sortedAmounts.map { abs(it - median) / median }.average()
        val amountScore = 1.0 - amountVariance.coerceIn(0.0, variance) / variance * 0.3
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
        val DAY_MILLIS = AppConstants.DAY_MILLIS
        val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val SHORT_DATE = SimpleDateFormat("d MMM", Locale.US)
    }
}
