package com.fynnix.app.core.analytics

import com.fynnix.app.core.utils.AppConstants
import com.fynnix.app.features.subscriptions.data.SubscriptionMandateEntity
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
 * Detects recurring payments two ways:
 *  - Confirmed: an e-mandate/standing-instruction SMS directly named this merchant and
 *    its billing schedule — treated as ground truth, near-max confidence.
 *  - Inferred: no mandate SMS was seen, so fall back to the statistical heuristic
 *    (≥ 3 transactions to the same merchant with intervals roughly 25–35 days and
 *    amounts within ±15%). Confidence is higher when intervals tighten around 30 days
 *    and amounts cluster more closely.
 *
 * A merchant with a confirmed mandate is excluded from the statistical pass — the
 * mandate's own next-due-date and amount are more accurate than a computed average.
 */
@Singleton
class SubscriptionDetector @Inject constructor(
    private val dao: TransactionDao,
) {
    /**
     * Builds the merged subscription list. [candidateMerchants] comes from statistical
     * recurrence detection; [mandates] are confirmed registrations, which take priority
     * whenever a merchant appears in both.
     */
    suspend fun build(
        candidateMerchants: List<String>,
        mandates: List<SubscriptionMandateEntity>,
    ): List<SubscriptionUiModel> {
        val mandateMerchants = mandates.map { it.merchant.lowercase() }.toSet()
        val confirmed = mandates.map { buildFromMandate(it) }
        val inferred = candidateMerchants
            .filterNot { it.lowercase() in mandateMerchants }
            .mapNotNull { merchant ->
                val tx = dao.txByMerchant(merchant)
                if (tx.size < AppConstants.SUBSCRIPTION_MIN_OCCURRENCES) return@mapNotNull null
                buildSubscription(merchant, tx)
            }
        return confirmed + inferred
    }

    private suspend fun buildFromMandate(mandate: SubscriptionMandateEntity): SubscriptionUiModel {
        val sorted = dao.txByMerchant(mandate.merchant).sortedBy { it.dateTime }
        return SubscriptionUiModel(
            id = "mandate-${mandate.id}",
            name = mandate.merchant,
            amount = mandate.amount ?: sorted.lastOrNull()?.amount ?: 0.0,
            nextBillingDate = mandate.nextDueDate?.let { DATE_FORMAT.format(Date(it)) } ?: "Unknown",
            color = androidx.compose.ui.graphics.Color(0xFFE5B342),
            streakMonths = sorted.size,
            confidence = MANDATE_CONFIDENCE,
            history = sorted.takeLast(6).map {
                SubscriptionHistory(
                    date = SHORT_DATE.format(Date(it.dateTime)),
                    amount = it.amount,
                )
            }
        )
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

        /** A bank-confirmed mandate is ground truth, not a guess — near the confidence
         *  ceiling but not literal 1.0 so it still visually ranks below a hypothetical
         *  future "verified" tier. */
        const val MANDATE_CONFIDENCE = 0.99f
    }
}
