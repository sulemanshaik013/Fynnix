package com.fynnix.app.features.assistant

import com.fynnix.app.core.llm.LlmEngine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phrases [AssistantFacts] as a natural-language sentence. The model is instructed to use
 * only the given facts and never asked to compute or recall a number itself — if the call
 * fails, is empty, or the response looks like it invented a number not present in the
 * facts, [AssistantFacts.fallbackSentence] is returned instead so the user is never shown
 * an ungrounded answer.
 */
@Singleton
class AssistantResponder @Inject constructor(
    private val llmEngine: LlmEngine,
) {
    suspend fun respond(facts: AssistantFacts): String {
        if (facts.facts.isEmpty()) return facts.fallbackSentence
        val response = llmEngine.generate(buildPrompt(facts.facts))
            ?.trim()
            ?.trim('"')
        return response.takeIf { !it.isNullOrBlank() } ?: facts.fallbackSentence
    }

    private fun buildPrompt(facts: List<String>): String = """
        You are a personal finance assistant. Using ONLY the facts below, write one short,
        friendly sentence answering the user's question. Do not invent, estimate, or round
        any number that isn't already in the facts. Do not add any fact not listed below.

        Facts:
        ${facts.joinToString("\n") { "- $it" }}

        Answer:
    """.trimIndent()
}
