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
        val SENDER_BANK_MAP: Map<String, String> = mapOf(
            "HDFCBK" to "HDFC",   "HDFCBN" to "HDFC",
            "ICICIB" to "ICICI",  "ICICI"  to "ICICI",
            "SBINSG" to "SBI",    "SBIINB" to "SBI",   "SBISMS" to "SBI", "SBIBNK" to "SBI",
            "AXISBK" to "Axis",   "AXISBN" to "Axis",  "AXIS"   to "Axis",
            "KOTAKB" to "Kotak",  "KOTAK"  to "Kotak",
            "INDBNK" to "Indian Bank",
            "PNBSMS" to "PNB",    "PNBINB" to "PNB",
            "BOIIND" to "BOI",
            "YESBNK" to "Yes Bank",
            "IDFCBK" to "IDFC",   "IDFC"   to "IDFC",
            "FEDBK"  to "Federal Bank",
            "CANBNK" to "Canara", "CANARA" to "Canara",
            "BARODM" to "Bank of Baroda",
            "UNIONB" to "Union Bank",
            "CSBBNK" to "CSB",
            "RBLBNK" to "RBL",
            "SCBNKI" to "Standard Chartered",
            "PAYTMB" to "Paytm",  "PYTMSM" to "Paytm", "PAYTM" to "Paytm",
            "PHONEPE" to "PhonePe",
            "AMAZPB" to "Amazon Pay",
            "MOBIKW" to "Mobikwik",
        )
    }
}
