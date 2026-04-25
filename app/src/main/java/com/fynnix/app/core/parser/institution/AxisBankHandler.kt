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
 * Common Axis senders: `XX-AXISBK-S`, `AXISBK`, `AXISB`, `AXISBANK`.
 */
@Singleton
class AxisBankHandler @Inject constructor(
    amountExtractor: AmountExtractor,
    merchantExtractor: MerchantExtractor,
    accountExtractor: AccountExtractor,
    referenceExtractor: ReferenceExtractor,
    balanceExtractor: BalanceExtractor,
    dateExtractor: DateExtractor,
) : BaseIndianBankHandler(amountExtractor, merchantExtractor, accountExtractor, referenceExtractor, balanceExtractor, dateExtractor) {

    override val name: String = "Axis Bank"
    override val priority: Int = 95
    override val senderTokens: List<String> = listOf("AXISBK", "AXISBANK", "AXISB")
}
