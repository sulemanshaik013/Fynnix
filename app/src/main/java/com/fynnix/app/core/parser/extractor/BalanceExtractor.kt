package com.fynnix.app.core.parser.extractor

import com.fynnix.app.core.parser.contract.Extractor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pulls a balance snapshot when the bank includes one in the SMS.
 *
 * Matches:
 *   "Avl Bal: INR 12,345.67"
 *   "Available Balance: 12,345"
 *   "Balance is Rs.500"
 *   "Avl Lmt: 23,456.50"  (credit card available limit — surfaced as negative — caller decides)
 */
@Singleton
class BalanceExtractor @Inject constructor() : Extractor<Double> {

    private val patterns = listOf(
        Regex("(?:avl\\s*bal(?:ance)?|available\\s*bal(?:ance)?|balance\\s*is|bal:)\\s*[:.\\s]*\\s*(?:inr\\s*)?([\\d,]+(?:\\.\\d{1,2})?)"),
        Regex("(?:avl\\s*lmt|available\\s*limit|credit\\s*limit)\\s*[:.\\s]*\\s*(?:inr\\s*)?([\\d,]+(?:\\.\\d{1,2})?)"),
    )

    override fun extract(text: String): Double? {
        for (p in patterns) {
            val m = p.find(text) ?: continue
            val raw = m.groupValues[1].replace(",", "")
            return raw.toDoubleOrNull()
        }
        return null
    }
}
