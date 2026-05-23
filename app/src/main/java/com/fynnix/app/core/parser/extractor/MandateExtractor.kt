package com.fynnix.app.core.parser.extractor

import com.fynnix.app.core.parser.MandateDraft
import com.fynnix.app.core.parser.TransactionIntent
import com.fynnix.app.core.utils.MandateStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extracts a [MandateDraft] from an e-mandate / standing-instruction registration or
 * cancellation SMS. These describe a *future* recurring debit rather than money that
 * has already moved, so amount/date extraction here feeds a subscription record, never
 * a [com.fynnix.app.core.parser.TransactionDraft].
 */
@Singleton
class MandateExtractor @Inject constructor(
    private val amountExtractor: AmountExtractor,
    private val merchantExtractor: MerchantExtractor,
    private val dateExtractor: DateExtractor,
) {
    /**
     * Returns null when no merchant could be identified — a subscription record without
     * a merchant name is useless, so the event is dropped rather than stored blank.
     */
    fun extract(rawBody: String, cleanedBody: String, intent: TransactionIntent): MandateDraft? {
        val merchant = merchantExtractor.extract(rawBody).takeIf { it != FALLBACK_MERCHANT } ?: return null
        return MandateDraft(
            merchant = merchant,
            amount = amountExtractor.extract(cleanedBody),
            umn = extractUmn(rawBody),
            nextDueDate = extractNextDueDate(rawBody),
            status = if (intent == TransactionIntent.MANDATE_CANCELLED) MandateStatus.CANCELLED else MandateStatus.ACTIVE,
        )
    }

    private fun extractUmn(body: String): String {
        for (p in umnPatterns) {
            p.find(body)?.groupValues?.getOrNull(1)?.let { return it.uppercase() }
        }
        return ""
    }

    /**
     * The "next debit" date must be read from the text immediately following its own
     * anchor phrase, not just the first date found anywhere in the SMS — a registration
     * notice commonly also states the registration date itself earlier in the body, and
     * [DateExtractor] has no way to tell the two apart on its own.
     */
    private fun extractNextDueDate(body: String): Long? {
        val window = nextDueAnchor.find(body)?.groupValues?.getOrNull(1) ?: return null
        return dateExtractor.extract(window)
    }

    private companion object {
        const val FALLBACK_MERCHANT = "Transaction"

        val umnPatterns = listOf(
            Regex("umn\\s*[:\\-]?\\s*([A-Za-z0-9]{6,35})", RegexOption.IGNORE_CASE),
            Regex("mandate\\s*(?:id|ref(?:erence)?)\\s*[:\\-]?\\s*([A-Za-z0-9]{6,35})", RegexOption.IGNORE_CASE),
        )

        val nextDueAnchor = Regex(
            "(?:next\\s*debit|next\\s*deduction|will\\s*be\\s*debited\\s*on|due\\s*on|" +
                "scheduled\\s*on|debited\\s*on)\\s*(?:date)?\\s*[:\\-]?\\s*(.{0,20})",
            RegexOption.IGNORE_CASE,
        )
    }
}
