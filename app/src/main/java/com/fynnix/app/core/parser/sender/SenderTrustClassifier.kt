package com.fynnix.app.core.parser.sender

import com.fynnix.app.core.parser.TrustLevel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Classifies the SMS sender into a [TrustLevel].
 *
 * In India, telco DLT senders look like `XX-HDFCBK`, `JD-AXISBK`, `BP-PAYTMB`.
 * We strip the optional 2-char prefix and match against substring tables.
 *
 * Coverage:
 *  - All major private banks (HDFC, ICICI, Axis, Kotak, IDFC, Yes, IndusInd, RBL, Federal)
 *  - All large PSU banks (SBI, PNB, BOB, Canara, Union, BOI, IDBI, UCO, IOB, CBI, Syndicate)
 *  - Co-operative and regional banks via common sender token patterns
 *  - UPI / wallet / fintech providers
 */
@Singleton
class SenderTrustClassifier @Inject constructor() {

    private val verifiedBanks = setOf(
        // HDFC
        "HDFCBK", "HDFC",
        // ICICI
        "ICICIB", "ICICI",
        // SBI & subsidiaries
        "SBIINB", "SBIUPI", "SBICRD", "SBPSBI", "SBMSBI", "SBI",
        // Axis
        "AXISBK", "AXISBANK", "AXISB", "AXIS",
        // Kotak
        "KOTAKB", "KOTAK",
        // IDFC First
        "IDFCBK", "IDFCFB", "IDFC",
        // Yes Bank
        "YESBNK", "YESB", "YBL",
        // IndusInd
        "INDUSB", "INDUS",
        // Federal Bank
        "FEDERAL", "FEDBNK", "FEDBK",
        // RBL Bank
        "RBLBNK", "RBLBK", "RBL",
        // --- PSU Banks ---
        // Punjab National Bank
        "PNBSMS", "PNBMBL", "PNB",
        // Bank of Baroda
        "BARODA", "BOBSMS", "BOBTXN", "BOBBNK", "BOB",
        // Canara Bank
        "CANBNK", "CANARA", "CANBK",
        // Union Bank of India
        "UNIONB", "UBISMS", "UBI", "UNION",
        // Bank of India
        "BOIIND", "BOIBNK", "BOISBI", "BOI",
        // IDBI Bank
        "IDBIBK", "IDBIBNK", "IDBI",
        // UCO Bank
        "UCOBNK", "UCO",
        // Indian Overseas Bank
        "IOBSMS", "IOBANK", "IOB",
        // Central Bank of India
        "CBISMS", "CENTBK", "CBI",
        // Bank of Maharashtra
        "MAHBNK", "BOMBNK", "BOM",
        // Punjab & Sind Bank
        "PSBBNK", "PSB",
        // Indian Bank
        "INDBNK", "INDBK",
        // --- Private mid-tier ---
        "CITIBK", "CITI",
        "HSBCBK", "HSBC",
        "SCBNK",             // Standard Chartered
        "DNSKBK",            // Dhanlaxmi Bank
        "KVBBNK", "KVB",    // Karur Vysya Bank
        "CSBBNK", "CSB",    // CSB Bank (Catholic Syrian)
        "TMBBNK", "TMB",    // Tamilnad Mercantile Bank
        "JKBNK",             // J&K Bank
        "NKGSB",             // NKGSB Co-op Bank
        "SARASW",            // Saraswat Bank
    )

    private val verifiedUpi   = setOf("BHIM", "UPI")

    private val verifiedWallet = setOf(
        "PAYTM", "PAYTMB", "PYTMBK", "PYTM",
        "PHONPE", "PHNPE", "PHONEPE",
        "AMAZON", "AMZNPAY",
        "MOBKWK", "FREECHRG",
    )

    private val verifiedCard  = setOf("VISA", "MASTERCARD", "RUPAY", "AMEX")

    private val verifiedFintech = setOf(
        "CRED", "JUPITER", "FAMPAY", "SLICEIT", "SLICE", "GPAY", "NAVI",
        "FISDOM", "GROWW", "ZERODHA",
    )

    private val spamHints = setOf("LOAN", "OFFER", "REWARD", "WIN", "FREE", "BONUS")

    fun classify(rawSender: String): TrustLevel {
        val core = stripDltPrefix(rawSender).uppercase()
        return when {
            verifiedBanks.any   { core.contains(it) } -> TrustLevel.VERIFIED_BANK
            verifiedUpi.any     { core.contains(it) } -> TrustLevel.VERIFIED_UPI
            verifiedWallet.any  { core.contains(it) } -> TrustLevel.VERIFIED_WALLET
            verifiedCard.any    { core.contains(it) } -> TrustLevel.VERIFIED_CARD
            verifiedFintech.any { core.contains(it) } -> TrustLevel.VERIFIED_FINTECH
            spamHints.any       { core.contains(it) } -> TrustLevel.SPAM
            else                                       -> TrustLevel.UNKNOWN
        }
    }

    fun isSpam(rawSender: String): Boolean = classify(rawSender) == TrustLevel.SPAM

    /** Strip optional 2-char telco prefix like `XX-` or `XX_`. */
    private fun stripDltPrefix(sender: String): String {
        val trimmed = sender.trim()
        if (trimmed.length > 3 && (trimmed[2] == '-' || trimmed[2] == '_')) {
            return trimmed.substring(3)
        }
        return trimmed
    }
}
