package com.fynnix.app.features.assistant

/** The fixed, v1 query set the on-device LLM can route a question to — deliberately not
 *  open-ended, so every answer is grounded in a real query this app can actually run. */
enum class AssistantPeriod { TODAY, THIS_WEEK, THIS_MONTH, THIS_YEAR }

sealed interface AssistantIntent {
    data class CategorySpend(val categoryName: String, val period: AssistantPeriod) : AssistantIntent
    data class PeriodTotal(val period: AssistantPeriod) : AssistantIntent
    data object SubscriptionsList : AssistantIntent
    data object BudgetStatus : AssistantIntent
    data object NetWorth : AssistantIntent
    data object Unknown : AssistantIntent
}

/**
 * What [AssistantQueryExecutor] hands to [AssistantResponder]: the real computed values as
 * short plain-text facts, plus a deterministic sentence built from those same facts. The
 * LLM is only ever asked to restate [facts] in natural language — [fallbackSentence] is
 * what the user sees verbatim if that phrasing call fails or is unavailable, so the app
 * never blocks on the model for something table data already fully answers.
 */
data class AssistantFacts(
    val facts: List<String>,
    val fallbackSentence: String,
)

data class ChatMessage(
    val id: String,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long,
)
