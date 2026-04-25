package com.fynnix.app.core.parser.institution

import com.fynnix.app.core.parser.extractor.AccountExtractor
import com.fynnix.app.core.parser.extractor.AmountExtractor
import com.fynnix.app.core.parser.extractor.BalanceExtractor
import com.fynnix.app.core.parser.extractor.DateExtractor
import com.fynnix.app.core.parser.extractor.MerchantExtractor
import com.fynnix.app.core.parser.extractor.ReferenceExtractor
import javax.inject.Inject
import javax.inject.Singleton

/** Punjab National Bank. DLT IDs: PNBSMS, PNB. */
@Singleton
class PNBBankHandler @Inject constructor(
    amountExtractor: AmountExtractor,
    merchantExtractor: MerchantExtractor,
    accountExtractor: AccountExtractor,
    referenceExtractor: ReferenceExtractor,
    balanceExtractor: BalanceExtractor,
    dateExtractor: DateExtractor,
) : BaseIndianBankHandler(amountExtractor, merchantExtractor, accountExtractor, referenceExtractor, balanceExtractor, dateExtractor) {

    override val name: String = "Punjab National Bank"
    override val priority: Int = 90
    override val senderTokens: List<String> = listOf("PNBSMS", "PNB")
}
