package com.fynnix.app.core.parser.institution

import com.fynnix.app.core.parser.NormalizedSms
import com.fynnix.app.core.parser.TransactionDraft
import com.fynnix.app.core.parser.TransactionIntent
import com.fynnix.app.core.parser.TrustLevel
import com.fynnix.app.core.parser.contract.Extractor
import com.fynnix.app.core.parser.contract.InstitutionParser

/**
 * Template method base for Indian bank handlers. All extraction is delegated
 * to the shared `extractor/` components — bank-specific overrides should be
 * the exception, not the rule.
 */
abstract class BaseIndianBankHandler(
    protected val amountExtractor: Extractor<Double>,
    protected val merchantExtractor: Extractor<String>,
    protected val accountExtractor: Extractor<String>,
    protected val referenceExtractor: Extractor<String>,
    protected val balanceExtractor: Extractor<Double>,
    protected val dateExtractor: Extractor<Long>,
) : InstitutionParser {

    abstract val senderTokens: List<String>

    override val priority: Int = 90
    override val trustLevel: TrustLevel = TrustLevel.VERIFIED_BANK

    override fun canHandle(senderUpper: String): Boolean =
        senderTokens.any { senderUpper.contains(it) }

    override fun parse(sms: NormalizedSms, intent: TransactionIntent): TransactionDraft? {
        val amount = amountExtractor.extract(sms.cleanedBody) ?: return null
        return TransactionDraft(
            intent = intent,
            amount = amount,
            type = intent.toType(),
            merchant = merchantExtractor.extract(sms.raw.body) ?: "Transaction",
            date = dateExtractor.extract(sms.raw.body) ?: sms.raw.timestamp,
            reference = referenceExtractor.extract(sms.raw.body) ?: "",
            accountLast4 = accountExtractor.extract(sms.cleanedBody),
            balance = balanceExtractor.extract(sms.cleanedBody),
            handlerName = name,
            trust = trustLevel,
        )
    }
}
