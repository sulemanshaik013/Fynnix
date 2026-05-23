package com.fynnix.app.core.sms

import android.util.Log
import androidx.room.withTransaction
import com.fynnix.app.core.analytics.AccountResolver
import com.fynnix.app.core.database.AppDatabase
import com.fynnix.app.core.parser.MandateDraft
import com.fynnix.app.core.parser.ParseResult
import com.fynnix.app.core.parser.Pipeline
import com.fynnix.app.core.parser.ReminderDraft
import com.fynnix.app.core.parser.RejectionReason
import com.fynnix.app.core.parser.SmsInput
import com.fynnix.app.core.parser.TransactionDraft
import com.fynnix.app.core.utils.Hashing
import com.fynnix.app.core.utils.MandateStatus
import com.fynnix.app.core.utils.TransactionSource
import com.fynnix.app.features.categories.domain.CategoryClassifier
import com.fynnix.app.features.reminders.data.BillReminderDao
import com.fynnix.app.features.reminders.data.BillReminderEntity
import com.fynnix.app.features.subscriptions.data.SubscriptionMandateDao
import com.fynnix.app.features.subscriptions.data.SubscriptionMandateEntity
import com.fynnix.app.features.transactions.data.FailedTransactionDao
import com.fynnix.app.features.transactions.data.FailedTransactionEntity
import com.fynnix.app.features.transactions.data.TransactionDao
import com.fynnix.app.features.transactions.data.TransactionEntity
import java.util.Calendar
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
    private val database: AppDatabase,
    private val transactionDao: TransactionDao,
    private val failedDao: FailedTransactionDao,
    private val categoryClassifier: CategoryClassifier,
    private val accountResolver: AccountResolver,
    private val subscriptionMandateDao: SubscriptionMandateDao,
    private val billReminderDao: BillReminderDao,
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
            is ParseResult.MandateEvent -> handleMandateEvent(raw, result.mandate)
            is ParseResult.ReminderEvent -> handleReminderEvent(raw, result.reminder)
            is ParseResult.Rejected -> handleRejection(raw, result)
        }
    }

    /**
     * Registers, updates, or cancels a [SubscriptionMandateEntity] instead of creating a
     * transaction — no money has moved yet. Wrapped in the same transaction pattern as
     * [handleSuccess] since the read-then-write (find existing row, then insert or update)
     * is not otherwise atomic against a concurrent sync processing the same merchant.
     */
    private suspend fun handleMandateEvent(raw: RawSms, mandate: MandateDraft): IngestionResult =
        database.withTransaction {
            if (mandate.status == MandateStatus.CANCELLED) {
                subscriptionMandateDao.cancelByMerchant(mandate.merchant, raw.timestamp)
                return@withTransaction IngestionResult(skipped = 1)
            }
            val existing = subscriptionMandateDao.findByMerchant(mandate.merchant)
            if (existing != null) {
                subscriptionMandateDao.updateActive(
                    id = existing.id,
                    amount = mandate.amount ?: existing.amount,
                    nextDueDate = mandate.nextDueDate ?: existing.nextDueDate,
                    umn = mandate.umn.ifBlank { existing.umn },
                    updatedAt = raw.timestamp,
                )
            } else {
                subscriptionMandateDao.insert(
                    SubscriptionMandateEntity(
                        merchant = mandate.merchant,
                        amount = mandate.amount,
                        umn = mandate.umn,
                        nextDueDate = mandate.nextDueDate,
                        status = MandateStatus.ACTIVE,
                        createdAt = raw.timestamp,
                        updatedAt = raw.timestamp,
                    )
                )
            }
            IngestionResult(skipped = 1)
        }

    /**
     * Registers or updates a [BillReminderEntity] — never a transaction, since a reminder
     * describes an upcoming due date, not money that has moved. Resets
     * [BillReminderEntity.notifiedForDate] only when the due date actually changed, so a
     * re-parse of the same unpaid bill's SMS (e.g. a re-sync) doesn't trigger a repeat
     * notification for a due date already alerted on.
     */
    private suspend fun handleReminderEvent(raw: RawSms, reminder: ReminderDraft): IngestionResult =
        database.withTransaction {
            val existing = billReminderDao.findByLabel(reminder.label)
            if (existing != null) {
                val notifiedForDate = if (existing.dueDate == reminder.dueDate) existing.notifiedForDate else null
                billReminderDao.updateReminder(
                    id = existing.id,
                    amount = reminder.amount ?: existing.amount,
                    dueDate = reminder.dueDate,
                    notifiedForDate = notifiedForDate,
                    updatedAt = raw.timestamp,
                )
            } else {
                billReminderDao.insert(
                    BillReminderEntity(
                        label = reminder.label,
                        amount = reminder.amount,
                        dueDate = reminder.dueDate,
                        createdAt = raw.timestamp,
                        updatedAt = raw.timestamp,
                    )
                )
            }
            IngestionResult(skipped = 1)
        }

    private suspend fun handleSuccess(
        raw: RawSms,
        draft: TransactionDraft,
        smsHash: String,
    ): IngestionResult {
        val dateTime = resolveDateTime(draft.date, raw.timestamp)
        val dedupKey = buildDedupKey(draft, dateTime)

        // Resolving-or-creating the account and then inserting a transaction that
        // references it must be one atomic unit: concurrent incremental syncs for two
        // messages that resolve to the same brand-new account can otherwise interleave
        // between the two steps and trip the accountId foreign key on a row that a
        // concurrent transaction is still in the middle of creating.
        return database.withTransaction {
            // Layer 2: coarse cross-channel dedup
            val existing = transactionDao.findByDedupKey(dedupKey)
            if (existing != null && !existing.isUserEdited) {
                return@withTransaction IngestionResult(duplicates = 1)
            }

            // Layer 2.5: a UPI reference number (UTR/RRN) identifies the real-world
            // movement itself, not the SMS reporting it — the bank's own alert and the
            // UPI app's courtesy notification for the same transfer carry the identical
            // reference despite different sender/wording. This is a stronger signal than
            // amount+time proximity, so check it first when the draft has one. Bounded to
            // REFERENCE_REUSE_WINDOW_MS because banks legitimately reuse the same UMRN
            // across separate autopay/mandate billing cycles.
            // Short/generic captures (e.g. a 6-char alphanumeric code two unrelated
            // merchants could plausibly share) are not distinctive enough to trust as a
            // duplicate signal — only match on references long enough to be a real UTR/RRN.
            if (draft.reference.length >= MIN_TRUSTED_REFERENCE_LENGTH) {
                val byReference = transactionDao.findByReferenceAmountAndType(
                    reference = draft.reference,
                    type = draft.type,
                    amountMin = draft.amount - AMOUNT_EPSILON,
                    amountMax = draft.amount + AMOUNT_EPSILON,
                    start = dateTime - REFERENCE_REUSE_WINDOW_MS,
                    end = dateTime + REFERENCE_REUSE_WINDOW_MS,
                )
                if (byReference != null && !byReference.isUserEdited) {
                    return@withTransaction IngestionResult(duplicates = 1)
                }
            }

            // Layer 2.6: the same real-world movement is often reported by two
            // different senders — a bank's own SMS and, separately, the UPI app's
            // courtesy notification for the same debit/credit (or the two legs of a
            // self-transfer each getting a bank + an app notification). Merchant
            // text frequently differs between bank and app wording, so the dedupKey
            // above can miss it. Same amount, same type, within a tight window is
            // almost certainly the same event reported twice, regardless of which
            // sender's message was processed first.
            val nearby = transactionDao.findNearbyByAmountAndType(
                type = draft.type,
                amountMin = draft.amount - AMOUNT_EPSILON,
                amountMax = draft.amount + AMOUNT_EPSILON,
                start = dateTime - CROSS_SENDER_WINDOW_MS,
                end = dateTime + CROSS_SENDER_WINDOW_MS,
            )
            if (nearby != null && !nearby.isUserEdited) {
                return@withTransaction IngestionResult(duplicates = 1)
            }

            val tx = buildTransaction(raw, draft, smsHash, dedupKey, dateTime)
            val rowId = transactionDao.insertIgnore(tx)
            if (rowId > 0) IngestionResult(saved = 1) else IngestionResult(duplicates = 1)
        }
    }

    /**
     * When the extractor found a date but no time, SimpleDateFormat defaults the
     * time-of-day to midnight — if that parsed date is the same calendar day the SMS
     * actually arrived, splice in the real arrival time-of-day instead of showing
     * 00:00. A different day with no time info stays at midnight for that day; there's
     * no better time to show.
     */
    private fun resolveDateTime(draftDate: Long, receivedAt: Long): Long {
        if (draftDate <= 0) return receivedAt
        val draftCal = Calendar.getInstance().apply { timeInMillis = draftDate }
        val isMidnight = draftCal.get(Calendar.HOUR_OF_DAY) == 0 &&
            draftCal.get(Calendar.MINUTE) == 0 &&
            draftCal.get(Calendar.SECOND) == 0
        if (!isMidnight) return draftDate

        val receivedCal = Calendar.getInstance().apply { timeInMillis = receivedAt }
        val sameDay = draftCal.get(Calendar.YEAR) == receivedCal.get(Calendar.YEAR) &&
            draftCal.get(Calendar.DAY_OF_YEAR) == receivedCal.get(Calendar.DAY_OF_YEAR)
        return if (sameDay) receivedAt else draftDate
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
        dateTime: Long,
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
            dateTime          = dateTime,
            source            = TransactionSource.SMS,
            smsReference      = draft.reference,
            rawMessage        = raw.body,
            messageSource     = raw.sender,
            parsingConfidence = draft.confidence / 100f,
            smsHash           = smsHash,
            dedupKey          = dedupKey,
        )
    }

    private fun buildDedupKey(draft: TransactionDraft, dateTime: Long): String {
        // Round rather than truncate: binary floating-point error can put amount*100
        // just under the intended integer (e.g. 19.99*100 == 1998.9999999999998),
        // which truncation would misread as 1998 paise instead of 1999.
        val amountCents  = Math.round(draft.amount * 100)
        val dayBucket    = (dateTime / DAY_MILLIS).toString()
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
        const val AMOUNT_EPSILON = 0.01
        const val CROSS_SENDER_WINDOW_MS = 3L * 60 * 1000
        const val REFERENCE_REUSE_WINDOW_MS = 24L * 60 * 60 * 1000
        const val MIN_TRUSTED_REFERENCE_LENGTH = 8
    }
}
