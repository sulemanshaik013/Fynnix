package com.fynnix.app.features.assistant

import com.fynnix.app.core.llm.LlmEngine
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Classifies a free-text question into one of the fixed [AssistantIntent]s — the LLM never
 * answers the question itself here, only picks which real query to run and with what
 * parameters. Any output that doesn't cleanly match the schema falls back to
 * [AssistantIntent.Unknown] rather than being guessed at.
 */
@Singleton
class AssistantIntentRouter @Inject constructor(
    private val llmEngine: LlmEngine,
) {
    suspend fun route(userText: String): AssistantIntent {
        val response = llmEngine.generate(buildPrompt(userText)) ?: return AssistantIntent.Unknown
        return runCatching { AssistantIntentParser.parse(response) }.getOrDefault(AssistantIntent.Unknown)
    }

    private fun buildPrompt(userText: String): String = """
        You are a router for a personal finance app. Read the user's question and output
        ONLY a single JSON object on one line, no other text, no markdown.

        Schema: {"intent": one of ["CATEGORY_SPEND", "PERIOD_TOTAL", "SUBSCRIPTIONS", "BUDGET_STATUS", "NET_WORTH", "UNKNOWN"], "category": string or null, "period": one of ["TODAY", "THIS_WEEK", "THIS_MONTH", "THIS_YEAR"] or null}

        Rules:
        - CATEGORY_SPEND needs "category" set to the spending category the user named (e.g. "Food", "Travel", "Shopping").
        - PERIOD_TOTAL is for overall spend/income totals with no specific category.
        - SUBSCRIPTIONS is for questions about recurring payments or subscriptions.
        - BUDGET_STATUS is for questions about how budgets are tracking.
        - NET_WORTH is for questions about total balance or net worth across accounts.
        - Default "period" to "THIS_MONTH" if the user didn't specify a time frame.
        - Use UNKNOWN for anything else, including greetings or questions this app cannot answer.

        Question: "How much did I spend on food this week?"
        JSON: {"intent": "CATEGORY_SPEND", "category": "Food", "period": "THIS_WEEK"}

        Question: "What's my total spend this month?"
        JSON: {"intent": "PERIOD_TOTAL", "category": null, "period": "THIS_MONTH"}

        Question: "What subscriptions do I have?"
        JSON: {"intent": "SUBSCRIPTIONS", "category": null, "period": null}

        Question: "Am I over budget anywhere?"
        JSON: {"intent": "BUDGET_STATUS", "category": null, "period": null}

        Question: "What's my net worth?"
        JSON: {"intent": "NET_WORTH", "category": null, "period": null}

        Question: "What's the weather today?"
        JSON: {"intent": "UNKNOWN", "category": null, "period": null}

        Question: "${userText.replace("\"", "'").take(300)}"
        JSON:
    """.trimIndent()
}

/**
 * Pure parsing/validation of the router's raw text response, split out from
 * [AssistantIntentRouter] so it can be unit-tested against canned strings without
 * constructing an [LlmEngine].
 */
internal object AssistantIntentParser {
    fun parse(rawResponse: String): AssistantIntent {
        val json = extractJsonObject(rawResponse) ?: return AssistantIntent.Unknown
        return parseIntent(json)
    }

    fun parseIntent(json: JSONObject): AssistantIntent {
        val period = parsePeriod(json.optString("period", "THIS_MONTH"))
        return when (json.optString("intent", "UNKNOWN").uppercase()) {
            "CATEGORY_SPEND" -> {
                val category = json.optString("category", "").trim()
                if (category.isBlank()) AssistantIntent.Unknown
                else AssistantIntent.CategorySpend(category, period)
            }
            "PERIOD_TOTAL" -> AssistantIntent.PeriodTotal(period)
            "SUBSCRIPTIONS" -> AssistantIntent.SubscriptionsList
            "BUDGET_STATUS" -> AssistantIntent.BudgetStatus
            "NET_WORTH" -> AssistantIntent.NetWorth
            else -> AssistantIntent.Unknown
        }
    }

    private fun parsePeriod(raw: String): AssistantPeriod = when (raw.uppercase()) {
        "TODAY" -> AssistantPeriod.TODAY
        "THIS_WEEK" -> AssistantPeriod.THIS_WEEK
        "THIS_YEAR" -> AssistantPeriod.THIS_YEAR
        else -> AssistantPeriod.THIS_MONTH
    }

    fun extractJsonObject(text: String): JSONObject? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start == -1 || end == -1 || end < start) return null
        return runCatching { JSONObject(text.substring(start, end + 1)) }.getOrNull()
    }
}
