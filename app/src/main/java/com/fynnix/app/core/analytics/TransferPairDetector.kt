package com.fynnix.app.core.analytics

import android.util.Log
import com.fynnix.app.core.utils.AccountType
import com.fynnix.app.core.utils.TransactionType
import com.fynnix.app.features.accounts.data.AccountDao
import com.fynnix.app.features.transactions.data.TransactionDao
import com.fynnix.app.features.transactions.data.TransactionEntity
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Detects internal transfers between the user's own accounts and marks both
 * sides as `isExcluded` so monthly spend totals don't double-count.
 *
 * Most common case — credit-card bill paid from a savings account:
 *
 *   T1: SAVINGS account, DEBIT, "towards Credit Card Bill", Rs.5000, day D
 *   T2: CREDIT_CARD account, CREDIT, "Payment received", Rs.5000, day D±2
 *
 * Without this detector, monthly spend reads as ₹10 000 even though only
 * one payment of ₹5 000 actually left the user's wallet (and the original
 * card swipes were already counted at swipe time).
 *
 * Pairing rules (all must hold):
 *   - One leg is DEBIT on a non-credit-card account; the other is CREDIT on
 *     a CREDIT_CARD or WALLET account.
 *   - Amount equality within ₹0.50.
 *   - dateTime within ±5 days.
 *   - Either body has a "card payment / bill / wallet load" keyword OR the
 *     two-account structure alone is enough when within ±2 days.
 *   - Neither leg has been user-edited.
 *
 * Idempotent — re-running over the same data is a no-op because both legs
 * are already `isExcluded`.
 */
@Singleton
class TransferPairDetector @Inject constructor(
    private val txDao: TransactionDao,
    private val accountDao: AccountDao,
) {

    /** Run the matcher over the entire current transaction set. Returns pair count. */
    suspend fun detectAndMark(): Int {
        val tx = txDao.getAllTransactions().first()
        if (tx.size < 2) return 0

        val accountTypes = buildAccountTypeMap()
        val candidates = tx.filter { it.accountId != null && !it.isExcluded && !it.isUserEdited }

        val byAccountType = candidates.groupBy { accountTypes[it.accountId] }

        val savingsLikeDebits = listOf(AccountType.SAVINGS, AccountType.WALLET, AccountType.UPI)
            .flatMap { byAccountType[it].orEmpty() }
            .filter { it.type == TransactionType.DEBIT }

        val opposingCredits = (
            byAccountType[AccountType.CREDIT_CARD].orEmpty() +
                byAccountType[AccountType.WALLET].orEmpty()
            ).filter { it.type == TransactionType.CREDIT }

        if (savingsLikeDebits.isEmpty() || opposingCredits.isEmpty()) return 0

        var paired = 0
        val usedCreditIds = mutableSetOf<String>()

        for (debit in savingsLikeDebits) {
            val match = opposingCredits.asSequence()
                .filter { it.id !in usedCreditIds }
                .filter { isMatch(debit, it, accountTypes) }
                .sortedBy { abs(it.dateTime - debit.dateTime) }
                .firstOrNull() ?: continue

            txDao.markExcluded(debit.id, true)
            txDao.markExcluded(match.id, true)
            usedCreditIds += match.id
            paired++
        }

        if (paired > 0) Log.d(TAG, "Marked $paired transfer pair(s) as excluded")
        return paired
    }

    private fun isMatch(
        debit: TransactionEntity,
        credit: TransactionEntity,
        accountTypes: Map<Long, AccountType>,
    ): Boolean {
        if (debit.accountId == null || credit.accountId == null) return false
        if (debit.accountId == credit.accountId) return false
        if (abs(debit.amount - credit.amount) > AMOUNT_EPSILON) return false

        val dt = abs(debit.dateTime - credit.dateTime)
        if (dt > LOOSE_WINDOW_MS) return false

        // A credit landing in a CREDIT_CARD account within the tight window is safe to
        // treat as a bill payment on structure alone — money only enters a credit-card
        // account via the cardholder's own payment, never an unrelated external credit.
        // Wallet credits are not equally safe: cashback/refunds routinely land in a
        // wallet with no relation to any specific debit, so those always require an
        // explicit keyword match regardless of how close the two legs are in time.
        val tight = dt <= TIGHT_WINDOW_MS
        val creditIsCreditCard = accountTypes[credit.accountId] == AccountType.CREDIT_CARD
        if (tight && creditIsCreditCard) return true

        return bodyMatchesBillPayment(debit) || bodyMatchesPaymentReceived(credit)
    }

    private fun bodyMatchesBillPayment(tx: TransactionEntity): Boolean {
        val b = tx.rawMessage.lowercase()
        return BILL_PAYMENT_KEYWORDS.any { b.contains(it) }
    }

    private fun bodyMatchesPaymentReceived(tx: TransactionEntity): Boolean {
        val b = tx.rawMessage.lowercase()
        return PAYMENT_RECEIVED_KEYWORDS.any { b.contains(it) }
    }

    private suspend fun buildAccountTypeMap(): Map<Long, AccountType> =
        accountDao.getAllAccounts().first().associate { it.id to it.type }

    private companion object {
        const val TAG = "TransferPairDetector"
        const val AMOUNT_EPSILON = 0.50
        const val TIGHT_WINDOW_MS = 2L * 24 * 60 * 60 * 1000
        const val LOOSE_WINDOW_MS = 5L * 24 * 60 * 60 * 1000

        val BILL_PAYMENT_KEYWORDS = listOf(
            "credit card bill", "credit card payment", "cc bill", "cc payment",
            "towards credit card", "towards card", "card bill payment",
            "wallet load", "wallet topup", "wallet top up", "topped up",
            "transfer to wallet", "added to wallet",
        )

        val PAYMENT_RECEIVED_KEYWORDS = listOf(
            "payment received", "payment of", "received towards",
            "credit received", "thank you for", "load successful",
            "wallet credited", "added to your wallet",
        )
    }
}
