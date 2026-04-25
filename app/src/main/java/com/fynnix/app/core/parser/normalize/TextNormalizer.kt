package com.fynnix.app.core.parser.normalize

import com.fynnix.app.core.parser.NormalizedSms
import com.fynnix.app.core.parser.SmsInput
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

/** Currency unify, NFKC, lowercase, collapse whitespace. Runs exactly once per SMS. */
@Singleton
class TextNormalizer @Inject constructor() {

    fun normalize(input: SmsInput): NormalizedSms {
        val nfkc = Normalizer.normalize(input.body, Normalizer.Form.NFKC)
        val cleaned = MULTI_SPACE.replace(
            CURRENCY_TOKEN.replace(nfkc.lowercase(), "inr"),
            " "
        ).trim()

        return NormalizedSms(
            raw = input,
            senderUpper = input.sender.uppercase(),
            cleanedBody = cleaned,
        )
    }

    private companion object {
        val CURRENCY_TOKEN = Regex("(?:rs\\.?|inr|₹)", RegexOption.IGNORE_CASE)
        val MULTI_SPACE = Regex("\\s+")
    }
}
