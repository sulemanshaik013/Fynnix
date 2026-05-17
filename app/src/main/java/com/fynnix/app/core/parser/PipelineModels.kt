package com.fynnix.app.core.parser

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
    OTP, PROMOTIONAL, LOAN_MARKETING, KYC, REMINDER, PAYMENT_REQUEST, NON_FINANCIAL;

    /** True iff this should produce a stored transaction. */
    val isFinancial: Boolean
        get() = when (this) {
            OTP, PROMOTIONAL, LOAN_MARKETING, KYC, REMINDER, PAYMENT_REQUEST, NON_FINANCIAL,
            FAILED_TRANSACTION -> false
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

/** Sealed result — every pipeline call ends here, no nulls, no exceptions. */
sealed interface ParseResult {
    data class Success(val draft: TransactionDraft) : ParseResult
    data class Rejected(
        val reason: RejectionReason,
        val handlerName: String? = null,
        val detail: String = "",
    ) : ParseResult
}
