package com.fynnix.app.core.parser

import com.fynnix.app.core.utils.MandateStatus
import com.fynnix.app.core.utils.TransactionType

/* --- Inputs --- */

/** Raw SMS handed to the pipeline. Pipeline never mutates this. */
data class SmsInput(
    val id: String,
    val sender: String,
    val body: String,
    val timestamp: Long,
    val simCountry: String = "IN",
)

/** Result of normalization — feeds every downstream stage. */
data class NormalizedSms(
    val raw: SmsInput,
    val senderUpper: String,
    val cleanedBody: String,    // lowercased, NFKC, currency unified, whitespace collapsed
)

/* --- Domain enums --- */

enum class TrustLevel(val score: Int) {
    VERIFIED_BANK(40),
    VERIFIED_UPI(35),
    VERIFIED_WALLET(35),
    VERIFIED_CARD(35),
    VERIFIED_FINTECH(25),
    UNKNOWN(0),
    SPAM(-40),
}

enum class TransactionIntent {
    MONEY_DEBIT, MONEY_CREDIT, PAYMENT_RECEIVED, REFUND,
    ATM_WITHDRAWAL, AUTOPAY, SUBSCRIPTION, TRANSFER,
    /** Genuinely failed or declined original — must NOT be persisted as a transaction. */
    FAILED_TRANSACTION,
    /**
     * e-mandate / standing-instruction registration and cancellation notices. No money has
     * moved yet, so these must never be persisted as a transaction — Pipeline routes them to
     * [ParseResult.MandateEvent] instead of the normal draft path.
     */
    MANDATE_REGISTERED, MANDATE_CANCELLED,
    OTP, PROMOTIONAL, LOAN_MARKETING, KYC, REMINDER, PAYMENT_REQUEST, NON_FINANCIAL;

    /** True iff this should produce a stored transaction. */
    val isFinancial: Boolean
        get() = when (this) {
            OTP, PROMOTIONAL, LOAN_MARKETING, KYC, REMINDER, PAYMENT_REQUEST, NON_FINANCIAL,
            FAILED_TRANSACTION, MANDATE_REGISTERED, MANDATE_CANCELLED -> false
            else -> true
        }

    fun toType(): TransactionType = when (this) {
        MONEY_CREDIT, PAYMENT_RECEIVED, REFUND -> TransactionType.CREDIT
        else -> TransactionType.DEBIT
    }
}

enum class RejectionReason {
    EMPTY_BODY, BODY_TOO_SHORT,
    OTP, PROMOTIONAL,
    SPAM_SENDER, NOT_FINANCIAL, UNKNOWN_INTENT,
    FAILED_TRANSACTION,
    NO_HANDLER, MISSING_AMOUNT, EXTRACTION_FAILED,
    LOW_CONFIDENCE,
}

/* --- Drafts + result --- */

/**
 * Mutable-by-copy snapshot produced by an InstitutionParser. Confidence is
 * filled in later by the ConfidenceScorer stage.
 */
data class TransactionDraft(
    val intent: TransactionIntent,
    val amount: Double,
    val type: TransactionType,
    val merchant: String,
    val date: Long,
    val reference: String = "",
    val accountLast4: String? = null,
    val balance: Double? = null,
    val confidence: Int = 0,           // populated by scorer
    val handlerName: String = "",      // populated by parser
    val trust: TrustLevel = TrustLevel.UNKNOWN,
)

/**
 * Extracted from an e-mandate / standing-instruction registration or cancellation SMS.
 * [amount] and [nextDueDate] describe a *future* recurring debit, not money that has
 * already moved — callers must not build a [TransactionDraft] from this.
 */
data class MandateDraft(
    val merchant: String,
    val amount: Double?,
    val umn: String,
    val nextDueDate: Long?,
    val status: MandateStatus,
)

/**
 * Extracted from a bill/statement due-date reminder SMS (credit card statement, loan EMI,
 * utility bill). [dueDate] is required — a reminder with no due date carries nothing
 * actionable, so [com.fynnix.app.core.parser.extractor.ReminderExtractor] never produces
 * one without it.
 */
data class ReminderDraft(
    val label: String,
    val amount: Double?,
    val dueDate: Long,
)

/** Sealed result — every pipeline call ends here, no nulls, no exceptions. */
sealed interface ParseResult {
    data class Success(val draft: TransactionDraft) : ParseResult
    data class MandateEvent(val mandate: MandateDraft) : ParseResult
    data class ReminderEvent(val reminder: ReminderDraft) : ParseResult
    data class Rejected(
        val reason: RejectionReason,
        val handlerName: String? = null,
        val detail: String = "",
    ) : ParseResult
}
