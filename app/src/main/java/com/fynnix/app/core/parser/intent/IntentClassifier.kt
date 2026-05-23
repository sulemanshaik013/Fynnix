package com.fynnix.app.core.parser.intent

import com.fynnix.app.core.parser.TransactionIntent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps a normalized SMS body to a [TransactionIntent].
 *
 * Order:
 *   1. KYC / promotional metadata wins over anything (avoids accidentally turning
 *      "KYC pending — failed" into FAILED_TRANSACTION).
 *   2. Pure balance pings → NON_FINANCIAL.
 *   3. Failed/declined → FAILED_TRANSACTION (never persisted).
 *   4. Refund / reversal of credit (positive case) → REFUND.
 *   5. Collect/payment requests → PAYMENT_REQUEST (nothing has moved yet — checked
 *      before the credit-verb pass since "you've received a payment request" contains
 *      "received" and would otherwise misclassify as MONEY_CREDIT).
 *   6. Mandate registration / cancellation → MANDATE_REGISTERED / MANDATE_CANCELLED
 *      (checked before AUTOPAY, since a registration notice also contains "e-mandate" or
 *      "standing instruction" but describes a *future* debit, not one that already happened).
 *   7. AUTOPAY / ATM detection.
 *   8. Plain debit / credit verbs.
 */
@Singleton
class IntentClassifier @Inject constructor() {

    fun classify(cleanedBody: String): TransactionIntent {
        val b = cleanedBody

        if (b.contains("kyc") || b.contains("update your")) return TransactionIntent.KYC
        if (b.contains("due date") || b.contains("payment due") ||
            b.contains("statement is ready")
        ) return TransactionIntent.REMINDER

        // Pure balance.
        if ((b.contains("available balance") || b.contains("avl bal") ||
                b.contains("current balance")) &&
            !hasDebitVerb(b) && !hasCreditVerb(b) && !isFailed(b)
        ) {
            return TransactionIntent.NON_FINANCIAL
        }

        // FAILED before REFUND so "reversed due to insufficient" is captured here.
        if (isFailed(b)) return TransactionIntent.FAILED_TRANSACTION

        if (b.contains("refund") || b.contains("reversed") || b.contains("reversal"))
            return TransactionIntent.REFUND

        if (isPaymentRequest(b)) return TransactionIntent.PAYMENT_REQUEST

        if (isMandateCancellation(b)) return TransactionIntent.MANDATE_CANCELLED
        if (isMandateRegistration(b)) return TransactionIntent.MANDATE_REGISTERED

        if (b.contains("autopay") || b.contains("auto-pay") || b.contains("auto debit") ||
            b.contains("e-mandate") || b.contains("standing instruction")
        ) return TransactionIntent.AUTOPAY

        if (b.contains("atm") && hasDebitVerb(b)) return TransactionIntent.ATM_WITHDRAWAL

        if (hasCreditVerb(b)) return TransactionIntent.MONEY_CREDIT
        if (hasDebitVerb(b)) return TransactionIntent.MONEY_DEBIT

        return TransactionIntent.NON_FINANCIAL
    }

    private fun isFailed(b: String): Boolean = listOf(
        "declined",
        "transaction failed", "txn failed", "payment failed",
        "insufficient balance", "insufficient funds",
        "could not be processed", "failed due to",
        "rejected by",
        "reversed due to",
        "authorization reversed", "auth reversed",
    ).any { b.contains(it) }

    private fun isPaymentRequest(b: String): Boolean = listOf(
        "has requested", "is requesting",
        "payment request", "collect request",
        "requesting payment", "requests rs", "requests inr",
        "ignore if already paid",
    ).any { b.contains(it) }

    /**
     * A mandate/autopay *setup* notice — no debit verb, since the money hasn't moved yet.
     * Distinct from AUTOPAY (an actual debit that happens to be recurring), which this
     * check must run before to avoid misrouting the registration itself as a transaction.
     */
    private fun isMandateRegistration(b: String): Boolean =
        (b.contains("mandate") || b.contains("standing instruction") || b.contains(" si ")) &&
            listOf("regist", "created", "is active", "activated", "has been set up", "setup successful")
                .any { b.contains(it) } &&
            !hasDebitVerb(b)

    private fun isMandateCancellation(b: String): Boolean =
        (b.contains("mandate") || b.contains("autopay") || b.contains("auto-pay") ||
            b.contains("standing instruction")) &&
            listOf("cancel", "deactivat", "stopped", "revoked").any { b.contains(it) }

    private fun hasDebitVerb(b: String): Boolean = listOf(
        "debited", "withdrawn", "spent", "paid",
        "purchase of", "payment of", "debit of",
        "transferred", "sent inr", "sent to", "sent ",
        "spent on",
    ).any { b.contains(it) }

    private fun hasCreditVerb(b: String): Boolean = listOf(
        "credited", "deposited", "received",
        "credit of", "received inr", "has been credited",
    ).any { b.contains(it) }
}
