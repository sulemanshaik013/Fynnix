package com.fynnix.app.core.parser.extractor

import com.fynnix.app.core.parser.ReminderDraft
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extracts a [ReminderDraft] from a bill/statement due-date SMS (credit card statement,
 * loan EMI, utility bill). A due date is the entire point of a reminder, so no draft is
 * produced without one — an amount-only or merchant-only reminder isn't actionable.
 */
@Singleton
class ReminderExtractor @Inject constructor(
    private val amountExtractor: AmountExtractor,
    private val merchantExtractor: MerchantExtractor,
    private val dateExtractor: DateExtractor,
) {
    fun extract(rawBody: String, cleanedBody: String, senderUpper: String): ReminderDraft? {
        val dueDate = extractDueDate(rawBody) ?: return null
        val merchant = merchantExtractor.extract(rawBody)
        val label = merchant.takeIf { it != FALLBACK_MERCHANT } ?: "Bill ($senderUpper)"
        return ReminderDraft(
            label = label,
            amount = amountExtractor.extract(cleanedBody),
            dueDate = dueDate,
        )
    }

    private fun extractDueDate(body: String): Long? {
        val window = dueDateAnchor.find(body)?.groupValues?.getOrNull(1) ?: return null
        return dateExtractor.extract(window)
    }

    private companion object {
        const val FALLBACK_MERCHANT = "Transaction"

        val dueDateAnchor = Regex(
            "(?:due\\s*date|due\\s*on|payment\\s*due(?:\\s*by)?|last\\s*date|pay\\s*by)" +
                "\\s*(?:is|on)?\\s*[:\\-]?\\s*(.{0,20})",
            RegexOption.IGNORE_CASE,
        )
    }
}
