package com.fynnix.app.core.parser.filter

import com.fynnix.app.core.parser.NormalizedSms
import com.fynnix.app.core.parser.ParseResult
import com.fynnix.app.core.parser.RejectionReason
import com.fynnix.app.core.parser.contract.SmsFilter
import javax.inject.Inject
import javax.inject.Singleton

/** Rejects OTP / verification messages BEFORE any financial parsing. */
@Singleton
class OtpFilter @Inject constructor() : SmsFilter {
    override val priority: Int = 10

    override fun evaluate(sms: NormalizedSms): ParseResult.Rejected? {
        val body = sms.cleanedBody
        val hit = KEYWORDS.any { body.contains(it) } || VALID_FOR_OTP.containsMatchIn(body)
        return if (hit) ParseResult.Rejected(RejectionReason.OTP) else null
    }

    private companion object {
        val KEYWORDS = listOf(
            "otp",
            "one-time password", "one time password",
            "do not share",
            "verification code", "verify code",
            "authentication code", "auth code",
            "security code",
        )

        // A bare "valid for" also appears in legitimate financial SMS (autopay/mandate
        // confirmations, disclaimer boilerplate), so only treat it as an OTP signal when
        // it's actually anchored to an expiry duration like "valid for 10 minutes".
        val VALID_FOR_OTP = Regex("valid for \\d+\\s*(minute|min|second|sec)s?\\b", RegexOption.IGNORE_CASE)
    }
}
