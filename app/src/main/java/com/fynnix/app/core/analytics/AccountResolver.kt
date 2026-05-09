package com.fynnix.app.core.analytics

import com.fynnix.app.core.parser.TransactionDraft
import com.fynnix.app.core.utils.AccountType
import com.fynnix.app.features.accounts.data.AccountDao
import com.fynnix.app.features.accounts.data.AccountEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps a parsed transaction → an [AccountEntity], creating one on first sight.
 *
 * Account identity = (bankName, lastFourDigits, type).
 *
 * Type detection rules (body-keyword based):
 *  - "credit card" / "credit limit" / "available limit"  → CREDIT_CARD
 *  - "wallet"                                            → WALLET
 *  - VPA-only with no last4                              → UPI (last4 = "")
 *  - everything else                                     → SAVINGS
 *
 * Bank name resolution priority:
 *  1. Handler name (if not "Generic" — i.e. a known-bank parser matched)
 *  2. Sender-code map (e.g. "HDFCBK" → "HDFC") — prevents duplicate "Other" accounts
 *  3. Generic fallback ("Wallet" / "UPI" / "Other")
 */
@Singleton
class AccountResolver @Inject constructor(
    private val accountDao: AccountDao,
) {

    /** Resolves or creates an account for [draft]. Returns its row id. */
    suspend fun resolve(draft: TransactionDraft, smsBodyLower: String, senderCode: String = ""): Long {
        val type = detectType(smsBodyLower, draft.handlerName)
        val bank = bankNameFor(draft.handlerName, type, senderCode)
        val last4 = draft.accountLast4?.takeIf { it.isNotBlank() } ?: ""

        val existing = accountDao.findByKey(bank, last4, type)
        if (existing != null) {
            val balance = draft.balance
            if (balance != null && draft.date > existing.updatedAt) {
                accountDao.setBalance(existing.id, balance, draft.date)
            }
            return existing.id
        }

        val name = displayName(bank, last4, type)
        val newRow = AccountEntity(
            name = name,
            type = type,
            bankName = bank,
            lastFourDigits = last4,
            balance = draft.balance ?: 0.0,
            isActive = true,
            updatedAt = draft.date,
        )
        val id = accountDao.insert(newRow)
        return if (id > 0) id else
            (accountDao.findByKey(bank, last4, type)?.id ?: -1L)
    }

    private fun detectType(body: String, handlerName: String): AccountType = when {
        body.contains("credit card") ||
            body.contains("credit limit") ||
            body.contains("available limit") ||
            body.contains("avl lmt") ||
            body.contains("crd ending") -> AccountType.CREDIT_CARD
        body.contains("wallet") -> AccountType.WALLET
        body.contains("vpa") && !body.contains("a/c") && !body.contains("ac no") &&
            !body.contains("account") -> AccountType.UPI
        else -> AccountType.SAVINGS
    }

    private fun bankNameFor(handlerName: String, type: AccountType, senderCode: String): String {
        if (handlerName != "Generic") return handlerName
        val fromSender = senderToBank(senderCode)
        return when (type) {
            AccountType.WALLET -> fromSender ?: "Wallet"
            AccountType.UPI -> fromSender ?: "UPI"
            else -> fromSender ?: "Other"
        }
    }

    private fun senderToBank(sender: String): String? {
        if (sender.isBlank()) return null
        val s = sender.uppercase()
        return SENDER_BANK_MAP.entries.firstOrNull { (key, _) -> s.contains(key) }?.value
    }

    private fun displayName(bank: String, last4: String, type: AccountType): String {
        val typeLabel = when (type) {
            AccountType.CREDIT_CARD -> "Credit Card"
            AccountType.WALLET -> "Wallet"
            AccountType.UPI -> "UPI"
            AccountType.SAVINGS -> "Savings"
        }
        return if (last4.isNotBlank()) "$bank $typeLabel ••$last4" else "$bank $typeLabel"
    }

    companion object {
        // Account identity is keyed on (bankName, last4, type) — these strings MUST match
        // each bank's InstitutionParser.name exactly (e.g. HDFCBankHandler.name). When a
        // sender falls through to the generic handler on one message but matches the
        // specific bank handler on another (a template variant the handler's senderTokens
        // doesn't cover, a transient parser miss, etc.), a mismatched name here silently
        // creates a second account row for the same real bank instead of resolving to the
        // one already on file.
        val SENDER_BANK_MAP: Map<String, String> = mapOf(
            "HDFCBK" to "HDFC Bank",   "HDFCBN" to "HDFC Bank",
            "ICICIB" to "ICICI Bank",  "ICICI"  to "ICICI Bank",
            "SBINSG" to "SBI",    "SBIINB" to "SBI",   "SBISMS" to "SBI", "SBIBNK" to "SBI",
            "AXISBK" to "Axis Bank",   "AXISBN" to "Axis Bank",  "AXIS"   to "Axis Bank",
            "KOTAKB" to "Kotak Mahindra Bank",  "KOTAK"  to "Kotak Mahindra Bank",
            "INDBNK" to "Indian Bank",
            "PNBSMS" to "Punjab National Bank",    "PNBINB" to "Punjab National Bank",
            "BOIIND" to "Bank of India",
            "YESBNK" to "Yes Bank",
            "IDFCBK" to "IDFC First Bank",   "IDFC"   to "IDFC First Bank",
            "FEDBK"  to "Federal Bank",
            "CANBNK" to "Canara Bank", "CANARA" to "Canara Bank",
            "BARODM" to "Bank of Baroda",
            "UNIONB" to "Union Bank of India",
            "INDUSB" to "IndusInd Bank", "INDUS" to "IndusInd Bank",
            "CSBBNK" to "CSB",
            "RBLBNK" to "RBL Bank",
            "SCBNKI" to "Standard Chartered",
            "PAYTMB" to "Paytm",  "PYTMSM" to "Paytm", "PAYTM" to "Paytm",
            "PHONEPE" to "PhonePe",
            "AMAZPB" to "Amazon Pay",
            "MOBIKW" to "Mobikwik",
        )
    }
}
