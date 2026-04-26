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
import com.fynnix.app.features.categories.domain.CategoryClassifier
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
    private val categoryClassifier: CategoryClassifier,
    private val accountResolver: AccountResolver,
) {
    /**
     * A single malformed or unexpected SMS must never abort an entire sync batch — that
     * would leave [SyncWorker] retrying the same window indefinitely, hitting the same
     * failure on every attempt with no forward progress. Any unexpected exception from
     * this message is therefore caught here and recorded as a failure so the caller can
     * keep processing the rest of the batch.
     */
    suspend fun ingest(raw: RawSms): IngestionResult = try {
        ingestOrThrow(raw)
    } catch (e: Exception) {
        Log.e(TAG, "Unexpected failure ingesting SMS id=${raw.id}", e)
        runCatching {
            failedDao.insert(
                FailedTransactionEntity(
                    rawSMS    = raw.body,
                    reason    = "INGESTION_ERROR (${e::class.simpleName}: ${e.message})",
                    timestamp = raw.timestamp,
                )
            )
        }
        IngestionResult(failed = 1)
    }

    private suspend fun ingestOrThrow(raw: RawSms): IngestionResult {
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
        val accountId = accountResolver.resolve(draft, raw.body.lowercase(), raw.sender)
        return TransactionEntity(
            // The SMS-extracted reference (UMRN/UTR) is not safe as a primary key: banks
            // legitimately reuse the same reference across multiple billing cycles for
            // autopay/mandate debits, which would make every cycle after the first
            // silently collide with — and get dropped as a "duplicate" of — the first.
            // raw.id is unique per source message and is what identity must be keyed on.
            id                = raw.id,
            amount            = draft.amount,
            currency          = "INR",
            type              = draft.type,
            merchant          = draft.merchant,
            categoryId        = categoryClassifier.classify(draft.merchant, draft.amount),
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

    private fun buildDedupKey(draft: TransactionDraft): String {
        // Round rather than truncate: binary floating-point error can put amount*100
        // just under the intended integer (e.g. 19.99*100 == 1998.9999999999998),
        // which truncation would misread as 1998 paise instead of 1999.
        val amountCents  = Math.round(draft.amount * 100)
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
