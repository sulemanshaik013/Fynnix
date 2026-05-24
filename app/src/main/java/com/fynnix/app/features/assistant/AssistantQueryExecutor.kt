package com.fynnix.app.features.assistant

import com.fynnix.app.core.analytics.TimeRange
import com.fynnix.app.core.analytics.TimeRanges
import com.fynnix.app.core.utils.AccountType
import com.fynnix.app.features.accounts.data.AccountDao
import com.fynnix.app.features.budgets.data.BudgetDao
import com.fynnix.app.features.categories.data.CategoryDao
import com.fynnix.app.features.subscriptions.data.SubscriptionMandateDao
import com.fynnix.app.features.transactions.data.TransactionDao
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Runs the real DAO query behind a routed [AssistantIntent] — every number the assistant
 *  ever shows a user comes from here, never from the LLM itself. */
@Singleton
class AssistantQueryExecutor @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val budgetDao: BudgetDao,
    private val subscriptionMandateDao: SubscriptionMandateDao,
    private val accountDao: AccountDao,
) {
    suspend fun execute(intent: AssistantIntent): AssistantFacts = when (intent) {
        is AssistantIntent.CategorySpend -> categorySpend(intent.categoryName, intent.period)
        is AssistantIntent.PeriodTotal -> periodTotal(intent.period)
        AssistantIntent.SubscriptionsList -> subscriptions()
        AssistantIntent.BudgetStatus -> budgetStatus()
        AssistantIntent.NetWorth -> netWorth()
        AssistantIntent.Unknown -> AssistantFacts(
            facts = emptyList(),
            fallbackSentence = "I can only answer questions about category spend, totals, " +
                "subscriptions, budgets, or net worth right now.",
        )
    }

    private suspend fun categorySpend(categoryName: String, period: AssistantPeriod): AssistantFacts {
        val periodLabel = period.label()
        val categoryId = categoryDao.findIdByName(categoryName)
            ?: return AssistantFacts(
                facts = listOf("category '$categoryName' was not found"),
                fallbackSentence = "I couldn't find a category named \"$categoryName\".",
            )
        val range = period.toRange()
        val rows = transactionDao.categorySpendFlow(range.start, range.end).first()
        val amount = rows.firstOrNull { it.categoryId == categoryId }?.total ?: 0.0
        return AssistantFacts(
            facts = listOf("category: $categoryName", "period: $periodLabel", "amount spent: ₹${amount.formatMoney()}"),
            fallbackSentence = "You've spent ₹${amount.formatMoney()} on $categoryName $periodLabel.",
        )
    }

    private suspend fun periodTotal(period: AssistantPeriod): AssistantFacts {
        val periodLabel = period.label()
        val range = period.toRange()
        val spent = transactionDao.sumDebitsFlow(range.start, range.end).first()
        val received = transactionDao.sumCreditsFlow(range.start, range.end).first()
        return AssistantFacts(
            facts = listOf(
                "period: $periodLabel",
                "total spent: ₹${spent.formatMoney()}",
                "total received: ₹${received.formatMoney()}",
            ),
            fallbackSentence = "You've spent ₹${spent.formatMoney()} and received ₹${received.formatMoney()} $periodLabel.",
        )
    }

    private suspend fun subscriptions(): AssistantFacts {
        val active = subscriptionMandateDao.findActiveFlow().first()
        if (active.isEmpty()) {
            return AssistantFacts(
                facts = listOf("no active subscriptions"),
                fallbackSentence = "You don't have any confirmed active subscriptions.",
            )
        }
        val monthlyTotal = active.sumOf { it.amount ?: 0.0 }
        val lines = active.take(MAX_LISTED_ITEMS).map { "${it.merchant}: ₹${(it.amount ?: 0.0).formatMoney()}" }
        return AssistantFacts(
            facts = listOf("active subscriptions: ${active.size}", "monthly total: ₹${monthlyTotal.formatMoney()}") + lines,
            fallbackSentence = "You have ${active.size} active subscription${if (active.size == 1) "" else "s"} " +
                "totaling ₹${monthlyTotal.formatMoney()}/month: ${lines.joinToString(", ")}.",
        )
    }

    private suspend fun budgetStatus(): AssistantFacts {
        val range = TimeRanges.thisMonth()
        val budgets = budgetDao.getAllBudgets().first()
        if (budgets.isEmpty()) {
            return AssistantFacts(
                facts = listOf("no budgets set"),
                fallbackSentence = "You haven't set up any budgets yet.",
            )
        }
        val spendRows = transactionDao.categorySpendFlow(range.start, range.end).first()
        val spendByCategory = spendRows.associate { it.categoryId to it.total }
        val lines = budgets.map { budget ->
            val category = categoryDao.getCategoryById(budget.categoryId)
            val name = category?.name ?: "Uncategorized"
            val spent = spendByCategory[budget.categoryId] ?: 0.0
            "$name: ₹${spent.formatMoney()} of ₹${budget.limitAmount.formatMoney()}" +
                if (spent > budget.limitAmount) " (over budget)" else ""
        }
        val overCount = budgets.count { (spendByCategory[it.categoryId] ?: 0.0) > it.limitAmount }
        return AssistantFacts(
            facts = listOf("budgets this month: ${budgets.size}", "over budget: $overCount") + lines,
            fallbackSentence = if (overCount == 0) {
                "All ${budgets.size} of your budgets are within limit this month."
            } else {
                "$overCount of your ${budgets.size} budgets are over limit this month: " +
                    lines.filter { it.endsWith("(over budget)") }.joinToString(", ")
            },
        )
    }

    private suspend fun netWorth(): AssistantFacts {
        val accounts = accountDao.getAllAccounts().first().filter { it.type != AccountType.CREDIT_CARD }
        val total = accounts.sumOf { it.balance }
        return AssistantFacts(
            facts = listOf("net worth: ₹${total.formatMoney()}", "accounts counted: ${accounts.size}"),
            fallbackSentence = "Your net worth is ₹${total.formatMoney()} across ${accounts.size} account${if (accounts.size == 1) "" else "s"}.",
        )
    }

    private fun AssistantPeriod.toRange(): TimeRange = when (this) {
        AssistantPeriod.TODAY -> TimeRanges.today()
        AssistantPeriod.THIS_WEEK -> TimeRanges.thisWeek()
        AssistantPeriod.THIS_MONTH -> TimeRanges.thisMonth()
        AssistantPeriod.THIS_YEAR -> TimeRanges.thisYear()
    }

    private fun AssistantPeriod.label(): String = when (this) {
        AssistantPeriod.TODAY -> "today"
        AssistantPeriod.THIS_WEEK -> "this week"
        AssistantPeriod.THIS_MONTH -> "this month"
        AssistantPeriod.THIS_YEAR -> "this year"
    }

    private fun Double.formatMoney(): String = "%,.0f".format(this)

    private companion object {
        const val MAX_LISTED_ITEMS = 8
    }
}
