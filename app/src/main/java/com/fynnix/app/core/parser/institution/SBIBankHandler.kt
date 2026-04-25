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
 * State Bank of India. Multiple DLT IDs depending on product line:
 *  - SBIINB  → internet banking
 *  - SBIUPI  → UPI alerts
 *  - SBICRD  → credit card
 *  - SBI / SBPSBI → generic
 */
@Singleton
class SBIBankHandler @Inject constructor(
    amountExtractor: AmountExtractor,
    merchantExtractor: MerchantExtractor,
    accountExtractor: AccountExtractor,
    referenceExtractor: ReferenceExtractor,
    balanceExtractor: BalanceExtractor,
    dateExtractor: DateExtractor,
) : BaseIndianBankHandler(amountExtractor, merchantExtractor, accountExtractor, referenceExtractor, balanceExtractor, dateExtractor) {

    override val name: String = "SBI"
    override val priority: Int = 95
    override val senderTokens: List<String> =
        listOf("SBIINB", "SBIUPI", "SBICRD", "SBPSBI", "SBI")
}
