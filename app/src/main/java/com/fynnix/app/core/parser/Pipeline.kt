package com.fynnix.app.core.parser

import com.fynnix.app.core.parser.contract.ConfidenceScorer
import com.fynnix.app.core.parser.contract.SmsFilter
import com.fynnix.app.core.parser.contract.TransactionValidator
import com.fynnix.app.core.parser.country.CountryAdapter
import com.fynnix.app.core.parser.institution.ParserRegistry
import com.fynnix.app.core.parser.intent.IntentClassifier
import com.fynnix.app.core.parser.normalize.TextNormalizer
import com.fynnix.app.core.parser.sender.SenderTrustClassifier
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SMS → ParseResult orchestrator.
 *
 * Pipeline does **only** stage sequencing. Every concrete decision lives
 * behind an interface in `contract/` so each stage is independently
 * testable and replaceable.
 *
 * Stage order (matches the design doc 1:1):
 *
 *   1. Normalize
 *   2. Filter chain (sanity → OTP → promo, by priority)
 *   3. Country adapter
 *   4. Sender trust check (SPAM rejected here)
 *   5. Intent classification
 *   6. Parser discovery (chain-of-responsibility via ParserRegistry)
 *   7. Extraction (template method inside the parser)
 *   8. Confidence scoring
 *   9. Validation (final accept/reject)
 */
@Singleton
class Pipeline @Inject constructor(
    private val normalizer: TextNormalizer,
    filters: Set<@JvmSuppressWildcards SmsFilter>,
    private val countryAdapter: CountryAdapter,
    private val senderTrust: SenderTrustClassifier,
    private val intentClassifier: IntentClassifier,
    private val registry: ParserRegistry,
    private val scorer: ConfidenceScorer,
    private val validator: TransactionValidator,
) {
    private val orderedFilters: List<SmsFilter> = filters.sortedBy { it.priority }

    fun process(input: SmsInput): ParseResult {
        // 1. Normalize
        val sms = normalizer.normalize(input)

        // 2. Filter chain (early rejections)
        for (f in orderedFilters) f.evaluate(sms)?.let { return it }

        // 3. Country adapter — strip DLT prefix etc.
        val canonicalSender = countryAdapter.adaptSender(input.sender, input.simCountry)

        // 4. Sender trust — classify the already country-adapted sender so trust
        // classification and handler dispatch always agree on the same canonical form.
        val trust = senderTrust.classify(canonicalSender)
        if (trust == TrustLevel.SPAM) return ParseResult.Rejected(RejectionReason.SPAM_SENDER)

        // 5. Intent
        val intent = intentClassifier.classify(sms.cleanedBody)
        if (intent == TransactionIntent.FAILED_TRANSACTION) {
            return ParseResult.Rejected(RejectionReason.FAILED_TRANSACTION, detail = intent.name)
        }
        if (!intent.isFinancial) return ParseResult.Rejected(RejectionReason.NOT_FINANCIAL, detail = intent.name)

        // 6. Parser discovery
        val parser = registry.find(canonicalSender.uppercase())
            ?: return ParseResult.Rejected(RejectionReason.NO_HANDLER)

        // 7. Extraction
        val draft = parser.parse(sms, intent)
            ?: return ParseResult.Rejected(RejectionReason.MISSING_AMOUNT, parser.name)

        // 8. Score (institution trust if sender was UNKNOWN)
        val effectiveTrust = if (trust == TrustLevel.UNKNOWN) parser.trustLevel else trust
        val scored = draft.copy(trust = effectiveTrust)
            .let { it.copy(confidence = scorer.score(sms, it)) }

        // 9. Validate
        return validator.validate(scored)
    }
}
