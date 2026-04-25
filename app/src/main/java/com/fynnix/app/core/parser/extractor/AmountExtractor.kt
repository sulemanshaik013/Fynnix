package com.fynnix.app.core.parser.extractor

import com.fynnix.app.core.parser.contract.Extractor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Multi-pattern fallback amount extraction.
 *
 * Critical invariants:
 *  1. Operate on a *balance-stripped* copy of the body so a leading
 *     "Avl Bal: INR 12,345" cannot steal the amount slot.
 *  2. Verb-anchored patterns are tried FIRST. Bare `inr X` only fires when
 *     no verb-anchored amount is found.
 *  3. Reject candidates that appear inside an account-number phrase
 *     (e.g. "a/c 9876543210", "card XX1234"). This is context-based, not
 *     digit-count-based — so ₹10,00,000 (ten lakh) is accepted correctly.
 */
@Singleton
class AmountExtractor @Inject constructor() : Extractor<Double> {

    private val balancePhrase = Regex(
        "(?:avl\\s*bal(?:ance)?|available\\s*bal(?:ance)?|" +
            "balance\\s*is|current\\s*balance|" +
            "avl\\s*lmt|available\\s*limit|credit\\s*limit)" +
            "[^.\\n]*?(?:inr\\s*)?[\\d,]+(?:\\.\\d{1,2})?",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Matches numbers that are part of an account/card identifier context.
     * We remove these substrings from the candidate text before extracting,
     * so they cannot be mistakenly picked up as amounts.
     */
    private val accountContextPhrase = Regex(
        "(?:a/?c|acct|account|card|xx|x{2,}|ending|no\\.?)\\s*[\\d ]{4,}",
        RegexOption.IGNORE_CASE,
    )

    private val verbAnchored = listOf(
        Regex(
            "(?:debited|credited|paid|spent|sent|received|withdrawn|deposited|charged|transferred)" +
                "\\s+(?:by\\s+|inr\\s+|of\\s+|with\\s+|rs\\.?\\s+|₹\\s+)?([\\d,]+(?:\\.\\d{1,2})?)"
        ),
        Regex("(?:purchase|payment|debit|credit)\\s+of\\s+(?:inr\\s+|rs\\.?\\s+|₹\\s+)?([\\d,]+(?:\\.\\d{1,2})?)"),
        Regex("\\bfor\\s+(?:inr\\s+|rs\\.?\\s+|₹\\s+)([\\d,]+(?:\\.\\d{1,2})?)"),
        Regex(
            "(?:inr|rs\\.?|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)\\s+" +
                "(?:debited|credited|paid|spent|sent|received|withdrawn|charged)"
        ),
    )

    private val currencyAnchored = listOf(
        Regex("(?:inr|rs\\.?|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)"),
        Regex("([\\d,]+(?:\\.\\d{1,2})?)\\s*(?:inr|rs\\.?|₹)"),
    )

    override fun extract(text: String): Double? {
        // Strip balance phrases first, then strip account-context phrases so
        // neither can masquerade as an amount.
        val withoutBalance = balancePhrase.replace(text, " ")
        val withoutAccount = accountContextPhrase.replace(withoutBalance, " ")

        for (p in verbAnchored) {
            val v = p.find(withoutAccount)?.let { sanitize(it.groupValues[1]) }
            if (v != null) return v
        }
        for (p in currencyAnchored) {
            val v = p.find(withoutAccount)?.let { sanitize(it.groupValues[1]) }
            if (v != null) return v
        }
        return null
    }

    private fun sanitize(raw: String): Double? {
        val cleaned = raw.replace(",", "")
        val v = cleaned.toDoubleOrNull() ?: return null
        // Reject zero, negative, or astronomically large values (> 1 crore).
        if (v <= 0.0 || v > 1e7) return null
        return v
    }
}
