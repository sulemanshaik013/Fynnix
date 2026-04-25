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
        return if (KEYWORDS.any { sms.cleanedBody.contains(it) })
            ParseResult.Rejected(RejectionReason.OTP)
        else null
    }

    private companion object {
        val KEYWORDS = listOf(
            "otp",
            "one-time password", "one time password",
            "do not share",
            "valid for",
            "verification code", "verify code",
            "authentication code", "auth code",
            "security code",
        )
    }
}
