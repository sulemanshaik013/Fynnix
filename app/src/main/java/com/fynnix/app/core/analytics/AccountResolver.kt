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
 * Type detection rules (cheap, body-keyword based):
 *  - "credit card" / "credit limit" / "available limit"  → CREDIT_CARD
 *  - "wallet"                                            → WALLET
 *  - VPA-only with no last4                              → UPI (last4 = "")
 *  - everything else                                     → SAVINGS
 *
 * Balance: if the SMS body contained a snapshot (`Avl Bal: …`) and is newer
 * than the stored snapshot, we update it. We do NOT compute running totals
 * from transactions — banks are authoritative.
 */
@Singleton
class AccountResolver @Inject constructor(
    private val accountDao: AccountDao,
) {

    /** Resolves or creates an account for [draft]. Returns its row id. */
    suspend fun resolve(draft: TransactionDraft, smsBodyLower: String): Long {
        val type = detectType(smsBodyLower, draft.handlerName)
        val bank = bankNameFor(draft.handlerName, type)
        val last4 = draft.accountLast4?.takeIf { it.isNotBlank() } ?: ""

        val existing = accountDao.findByKey(bank, last4, type)
        if (existing != null) {
            // Update balance if newer.
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
            // INSERT IGNORE returned -1 — race; re-fetch.
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

    private fun bankNameFor(handlerName: String, type: AccountType): String {
        // For UPI VPAs from generic handler, derive bank from VPA suffix later;
        // for now, fall back to the handler display name.
        return when (type) {
            AccountType.WALLET -> if (handlerName == "Generic") "Wallet" else handlerName
            AccountType.UPI -> if (handlerName == "Generic") "UPI" else handlerName
            else -> handlerName.takeIf { it != "Generic" } ?: "Other"
        }
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
}
