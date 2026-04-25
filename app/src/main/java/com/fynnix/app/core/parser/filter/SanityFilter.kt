package com.fynnix.app.core.parser.filter

import com.fynnix.app.core.parser.NormalizedSms
import com.fynnix.app.core.parser.ParseResult
import com.fynnix.app.core.parser.RejectionReason
import com.fynnix.app.core.parser.contract.SmsFilter
import javax.inject.Inject
import javax.inject.Singleton

/** Drops empty / too-short messages before any expensive work. */
@Singleton
class SanityFilter @Inject constructor() : SmsFilter {
    override val priority: Int = 0

    override fun evaluate(sms: NormalizedSms): ParseResult.Rejected? {
        val raw = sms.raw.body
        if (raw.isBlank()) return ParseResult.Rejected(RejectionReason.EMPTY_BODY)
        if (raw.length < MIN_LENGTH) return ParseResult.Rejected(RejectionReason.BODY_TOO_SHORT)
        return null
    }

    private companion object { const val MIN_LENGTH = 12 }
}
