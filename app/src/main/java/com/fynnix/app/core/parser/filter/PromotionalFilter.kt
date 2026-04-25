package com.fynnix.app.core.parser.filter

import com.fynnix.app.core.parser.NormalizedSms
import com.fynnix.app.core.parser.ParseResult
import com.fynnix.app.core.parser.RejectionReason
import com.fynnix.app.core.parser.contract.SmsFilter
import javax.inject.Inject
import javax.inject.Singleton

/** Drops loan offers, cashback ads, "click here" spam. */
@Singleton
class PromotionalFilter @Inject constructor() : SmsFilter {
    override val priority: Int = 20

    override fun evaluate(sms: NormalizedSms): ParseResult.Rejected? {
        return if (KEYWORDS.any { sms.cleanedBody.contains(it) })
            ParseResult.Rejected(RejectionReason.PROMOTIONAL)
        else null
    }

    /** Re-used by the scorer to apply a URL penalty. */
    fun hasSuspiciousUrl(rawBody: String): Boolean = URL_PATTERN.containsMatchIn(rawBody)

    private companion object {
        val KEYWORDS = listOf(
            "pre-approved", "pre approved",
            "instant loan", "loan offer",
            "apply now",
            "cashback offer",
            "click link", "click here",
            "limited period", "limited time",
            "reward points",
            "% off", "flat off",
            "exclusive offer",
            "win ", "you've won", "you have won",
        )
        val URL_PATTERN = Regex("https?://\\S+", RegexOption.IGNORE_CASE)
    }
}
