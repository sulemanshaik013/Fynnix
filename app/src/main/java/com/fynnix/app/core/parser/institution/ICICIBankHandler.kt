package com.fynnix.app.core.parser.institution

import com.fynnix.app.core.parser.extractor.AccountExtractor
import com.fynnix.app.core.parser.extractor.AmountExtractor
import com.fynnix.app.core.parser.extractor.MerchantExtractor
import com.fynnix.app.core.parser.extractor.BalanceExtractor
import com.fynnix.app.core.parser.extractor.DateExtractor
import com.fynnix.app.core.parser.extractor.ReferenceExtractor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Common ICICI senders: `XX-ICICIB-S`, `ICICIB`, `ICICIBANK`.
 * Body shapes: "Acct XXxxx is credited with INR ...", "ICICI Bank Acct
 * XXxxx debited for INR ... towards UPI", "USD 11.80 spent using ICICI Bank
 * Card" (multi-currency credit cards).
 */
@Singleton
class ICICIBankHandler @Inject constructor(
    amountExtractor: AmountExtractor,
    merchantExtractor: MerchantExtractor,
    accountExtractor: AccountExtractor,
    referenceExtractor: ReferenceExtractor,
    balanceExtractor: BalanceExtractor,
    dateExtractor: DateExtractor,
) : BaseIndianBankHandler(amountExtractor, merchantExtractor, accountExtractor, referenceExtractor, balanceExtractor, dateExtractor) {

    override val name: String = "ICICI Bank"
    override val priority: Int = 95
    override val senderTokens: List<String> = listOf("ICICIB", "ICICI")
}
