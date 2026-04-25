package com.fynnix.app.core.parser.institution

import com.fynnix.app.core.parser.NormalizedSms
import com.fynnix.app.core.parser.TransactionDraft
import com.fynnix.app.core.parser.TransactionIntent
import com.fynnix.app.core.parser.TrustLevel
import com.fynnix.app.core.parser.contract.InstitutionParser
import com.fynnix.app.core.parser.extractor.AccountExtractor
import com.fynnix.app.core.parser.extractor.AmountExtractor
import com.fynnix.app.core.parser.extractor.BalanceExtractor
import com.fynnix.app.core.parser.extractor.DateExtractor
import com.fynnix.app.core.parser.extractor.MerchantExtractor
import com.fynnix.app.core.parser.extractor.ReferenceExtractor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Final fallback in the chain — lowest priority, trust = UNKNOWN.
 *
 * Defense-in-depth: even though the Pipeline already gates on
 * [TransactionIntent.isFinancial] before calling parse(), this handler
 * enforces the same contract internally so it is safe if ever called
 * outside the pipeline (e.g. tests or future refactors).
 */
@Singleton
class GenericFinancialHandler @Inject constructor(
    private val amountExtractor: AmountExtractor,
    private val merchantExtractor: MerchantExtractor,
    private val accountExtractor: AccountExtractor,
    private val referenceExtractor: ReferenceExtractor,
    private val balanceExtractor: BalanceExtractor,
    private val dateExtractor: DateExtractor,
) : InstitutionParser {

    override val name: String = "Generic"
    override val priority: Int = 1
    override val trustLevel: TrustLevel = TrustLevel.UNKNOWN

    override fun canHandle(senderUpper: String): Boolean = true

    override fun parse(sms: NormalizedSms, intent: TransactionIntent): TransactionDraft? {
        // Hard gate: only produce a draft for genuinely financial intents.
        if (!intent.isFinancial) return null

        // Amount is mandatory — without it the draft is meaningless.
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
