package com.fynnix.app.core.parser.extractor

import com.fynnix.app.core.parser.contract.Extractor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountExtractor @Inject constructor() : Extractor<String> {

    // "a/c xx1234", "card ending 1234", "ac no. xxxx1234"
    private val patterns = listOf(
        Regex("(?:a/c|ac|account|card)[^\\d]{0,15}(\\d{4})"),
        Regex("ending\\s+(?:with\\s+)?(\\d{4})"),
        Regex("xx+(\\d{4})"),
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
