package com.fynnix.app.core.parser.contract

import com.fynnix.app.core.parser.NormalizedSms
import com.fynnix.app.core.parser.ParseResult

/**
 * A single fast-fail gate. Filters run in priority order (lower = earlier).
 *
 * - Sanity → priority 0
 * - OTP    → priority 10
 * - Promo  → priority 20
 *
 * Adding a new filter is OPEN: register it via Hilt multibinding, set its
 * priority, done. Pipeline does not change.
 */
interface SmsFilter {
    val priority: Int
    fun evaluate(sms: NormalizedSms): ParseResult.Rejected?
}
