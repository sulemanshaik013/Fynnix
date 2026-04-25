package com.fynnix.app.core.sms

import android.util.Log
import com.fynnix.app.core.analytics.AccountResolver
import com.fynnix.app.core.parser.ParseResult
import com.fynnix.app.core.parser.Pipeline
import com.fynnix.app.core.parser.RejectionReason
import com.fynnix.app.core.parser.SmsInput
import com.fynnix.app.core.parser.TransactionDraft
import com.fynnix.app.core.utils.Hashing
import com.fynnix.app.core.utils.TransactionSource
import com.fynnix.app.features.categories.data.CategoryDao
import com.fynnix.app.features.transactions.data.FailedTransactionDao
import com.fynnix.app.features.transactions.data.FailedTransactionEntity
import com.fynnix.app.features.transactions.data.TransactionDao
import com.fynnix.app.features.transactions.data.TransactionEntity
import javax.inject.Inject
import javax.inject.Singleton

data class IngestionResult(
    val saved: Int = 0,
    val duplicates: Int = 0,
    val failed: Int = 0,
    val skipped: Int = 0,
) {
    operator fun plus(other: IngestionResult) = IngestionResult(
        saved      = saved + other.saved,
        duplicates = duplicates + other.duplicates,
        failed     = failed + other.failed,
        skipped    = skipped + other.skipped,
    )
}

/**
 * Single-responsibility use case: takes one [RawSms], runs it through the
 * [Pipeline], deduplicates, and writes the result to the database.
 *
 * Extracted from [SyncWorker] so this logic can be unit-tested independently
 * of WorkManager and Android context.
 */
@Singleton
class TransactionIngestionUseCase @Inject constructor(
    private val pipeline: Pipeline,
    private val transactionDao: TransactionDao,
    private val failedDao: FailedTransactionDao,
    private val categoryDao: CategoryDao,
    private val accountResolver: AccountResolver,
) {
    suspend fun ingest(raw: RawSms): IngestionResult {
        val smsHash = Hashing.sha256Hex(raw.sender + "|" + raw.body)

        // Layer 1: exact SMS dedup
        if (transactionDao.findBySmsHash(smsHash) != null) {
            return IngestionResult(duplicates = 1)
        }

        return when (val result = pipeline.process(SmsInput(raw.id, raw.sender, raw.body, raw.timestamp))) {
            is ParseResult.Success -> handleSuccess(raw, result.draft, smsHash)
            is ParseResult.Rejected -> handleRejection(raw, result)
        }
    }

    private suspend fun handleSuccess(
        raw: RawSms,
        draft: TransactionDraft,
        smsHash: String,
    ): IngestionResult {
        val dedupKey = buildDedupKey(draft)

        // Layer 2: coarse cross-channel dedup
        val existing = transactionDao.findByDedupKey(dedupKey)
        if (existing != null && !existing.isUserEdited) {
            return IngestionResult(duplicates = 1)
        }

        val tx = buildTransaction(raw, draft, smsHash, dedupKey)
        val rowId = transactionDao.insertIgnore(tx)
        return if (rowId > 0) IngestionResult(saved = 1) else IngestionResult(duplicates = 1)
    }

    private suspend fun handleRejection(
        raw: RawSms,
        rejected: ParseResult.Rejected,
    ): IngestionResult {
        if (shouldSaveFailure(rejected.reason)) {
            runCatching {
                failedDao.insert(
                    FailedTransactionEntity(
                        rawSMS    = raw.body,
                        reason    = buildFailureReason(rejected),
                        timestamp = raw.timestamp,
                    )
                )
            }.onFailure { Log.w(TAG, "Failed to persist rejected SMS", it) }
            return IngestionResult(failed = 1)
        }
        return IngestionResult(skipped = 1)
    }

    private suspend fun buildTransaction(
        raw: RawSms,
        draft: TransactionDraft,
        smsHash: String,
        dedupKey: String,
    ): TransactionEntity {
        val accountId = accountResolver.resolve(draft, raw.body.lowercase())
        return TransactionEntity(
            id                = draft.reference.ifEmpty { raw.id },
            amount            = draft.amount,
            currency          = "INR",
            type              = draft.type,
            merchant          = draft.merchant,
            categoryId        = categorize(draft.merchant),
            accountId         = accountId.takeIf { it > 0 },
            dateTime          = if (draft.date > 0) draft.date else raw.timestamp,
            source            = TransactionSource.SMS,
            smsReference      = draft.reference,
            rawMessage        = raw.body,
            messageSource     = raw.sender,
            parsingConfidence = draft.confidence / 100f,
            smsHash           = smsHash,
            dedupKey          = dedupKey,
        )
    }

    private suspend fun categorize(merchant: String): Long {
        val m = merchant.lowercase()
        val name = when {
            listOf("swiggy", "zomato", "food", "restaurant", "starbucks", "cafe").any { m.contains(it) } -> "Food"
            listOf("uber", "ola", "rapido", "fuel", "petrol", "metro", "irctc").any { m.contains(it) } -> "Transport"
            listOf("amazon", "flipkart", "myntra", "shopping", "ajio").any { m.contains(it) } -> "Shopping"
            listOf("netflix", "spotify", "hotstar", "prime", "youtube").any { m.contains(it) } -> "Entertainment"
            listOf("pharmacy", "hospital", "clinic", "medplus", "apollo").any { m.contains(it) } -> "Health"
            listOf("electric", "bill", "recharge", "broadband", "gas").any { m.contains(it) } -> "Bills"
            listOf("salary", "payroll").any { m.contains(it) } -> "Income"
            else -> "Uncategorized"
        }
        return categoryDao.findIdByName(name) ?: 0L
    }

    private fun buildDedupKey(draft: TransactionDraft): String {
        val amountCents  = (draft.amount * 100).toLong()
        val dayBucket    = (draft.date / DAY_MILLIS).toString()
        val merchantNorm = draft.merchant.lowercase()
            .replace(Regex("[^a-z0-9]"), "")
            .take(24)
        return "$amountCents|$dayBucket|$merchantNorm"
    }

    private fun shouldSaveFailure(reason: RejectionReason): Boolean = reason in setOf(
        RejectionReason.LOW_CONFIDENCE,
        RejectionReason.MISSING_AMOUNT,
        RejectionReason.EXTRACTION_FAILED,
    )

    private fun buildFailureReason(rejected: ParseResult.Rejected): String {
        val base   = rejected.reason.name
        val detail = rejected.detail.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
        val handler = rejected.handlerName?.takeIf { it.isNotBlank() }?.let { " [$it]" } ?: ""
        return "$base$detail$handler"
    }

    private companion object {
        const val TAG = "TransactionIngestion"
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
