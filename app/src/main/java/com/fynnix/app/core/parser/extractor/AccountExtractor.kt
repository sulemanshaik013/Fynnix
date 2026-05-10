package com.fynnix.app.core.parser.extractor

import com.fynnix.app.core.parser.contract.Extractor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountExtractor @Inject constructor() : Extractor<String> {

    // "a/c xx1234", "card ending 1234", "ac no. xxxx1234". `\d*` before the capture
    // group consumes any leading digits of a longer, unmasked run first, so `(\d{4})`
    // always lands on the trailing 4 digits — without it, an account shown unmasked
    // (e.g. "a/c 1234567890") captured the FIRST 4 digits instead of the last 4,
    // producing a different last4 for the same real account than a properly masked
    // message ("a/c xx7890") would, and fragmenting it into two account records.
    private val patterns = listOf(
        Regex("(?:a/c|ac|account|card)[^\\d]{0,15}\\d*(\\d{4})(?!\\d)"),
        Regex("ending\\s+(?:with\\s+)?\\d*(\\d{4})(?!\\d)"),
        Regex("xx+\\d*(\\d{4})(?!\\d)"),
    )

    /** Returns the last-4 of an account/card if found. */
    override fun extract(text: String): String? {
        for (p in patterns) {
            val m = p.find(text)
            if (m != null) return m.groupValues[1]
        }
        return null
    }
}
