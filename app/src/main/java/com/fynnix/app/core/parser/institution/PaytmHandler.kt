package com.fynnix.app.core.parser.institution

import com.fynnix.app.core.parser.TrustLevel
import com.fynnix.app.core.parser.extractor.AccountExtractor
import com.fynnix.app.core.parser.extractor.AmountExtractor
import com.fynnix.app.core.parser.extractor.BalanceExtractor
import com.fynnix.app.core.parser.extractor.DateExtractor
import com.fynnix.app.core.parser.extractor.MerchantExtractor
import com.fynnix.app.core.parser.extractor.ReferenceExtractor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Paytm Wallet / Paytm Payments Bank. DLT IDs include `PYTMBK`, `PYTM`,
 * `PAYTMB`, sometimes `PAYTM`. Trust level is `VERIFIED_WALLET` so that
 * AccountResolver classifies the auto-created account as `WALLET` (or
 * `SAVINGS` when an A/c last-4 is detected — body keywords decide).
 */
@Singleton
class PaytmHandler @Inject constructor(
    amountExtractor: AmountExtractor,
    merchantExtractor: MerchantExtractor,
    accountExtractor: AccountExtractor,
    referenceExtractor: ReferenceExtractor,
    balanceExtractor: BalanceExtractor,
    dateExtractor: DateExtractor,
) : BaseIndianBankHandler(amountExtractor, merchantExtractor, accountExtractor, referenceExtractor, balanceExtractor, dateExtractor) {

    override val name: String = "Paytm"
    override val priority: Int = 92
    override val trustLevel: TrustLevel = TrustLevel.VERIFIED_WALLET
    override val senderTokens: List<String> = listOf("PYTMBK", "PAYTMB", "PYTM", "PAYTM")
}
