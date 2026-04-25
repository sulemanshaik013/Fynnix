package com.fynnix.app.core.parser.extractor

import com.fynnix.app.core.parser.contract.Extractor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReferenceExtractor @Inject constructor() : Extractor<String> {

    // Run on the *raw* body so we keep alphanumeric casing intact.
    private val patterns = listOf(
        Regex("(?:Ref(?:erence)?(?:\\s*No\\.?)?|Txn(?:\\s*Id)?|Transaction\\s*Id|UPI(?:\\s*Ref)?)\\s*[:#]?\\s*([A-Za-z0-9]{6,})", RegexOption.IGNORE_CASE),
        Regex("\\bUTR[\\s:#]*([A-Za-z0-9]{6,})", RegexOption.IGNORE_CASE),
    )

    override fun extract(text: String): String? {
        for (p in patterns) {
            val m = p.find(text)
            if (m != null) return m.groupValues[1]
        }
        return null
    }
}
