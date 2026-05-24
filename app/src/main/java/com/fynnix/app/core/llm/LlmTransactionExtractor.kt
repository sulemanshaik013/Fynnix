package com.fynnix.app.core.llm

import android.util.Log
import com.fynnix.app.core.parser.TransactionDraft
import com.fynnix.app.core.parser.TransactionIntent
import com.fynnix.app.core.parser.TrustLevel
import com.fynnix.app.core.utils.TransactionType
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Last-resort extractor for a transactional-looking SMS the deterministic pipeline
 * couldn't handle (no per-bank handler matched, or extraction failed). Only ever invoked
 * for messages that already passed sender-trust filtering upstream — this is not a
 * substitute for that filter, so it never sees OTP/promo/spam messages in practice, but
 * the prompt still guards against them since a novel sender template could still be
 * financial-adjacent without being a real transaction.
 */
@Singleton
class LlmTransactionExtractor @Inject constructor(
    private val llmEngine: LlmEngine,
) {
    suspend fun extract(body: String, sender: String): TransactionDraft? {
        val response = llmEngine.generate(buildPrompt(body)) ?: return null
        return runCatching { LlmTransactionJsonParser.parse(response) }
            .onFailure { Log.w(TAG, "LLM extraction produced unusable output for sender=$sender", it) }
            .getOrNull()
    }

    private fun buildPrompt(body: String): String = """
        You are a strict financial SMS classifier. Read the SMS below and output ONLY a
        single JSON object on one line, with no other text, no markdown, no explanation.

        Schema: {"isTransaction": boolean, "type": "DEBIT" or "CREDIT", "amount": number, "merchant": string, "accountLast4": 4-digit string or null}

        Rules:
        - isTransaction is false for OTPs, promotions, balance/limit alerts with no
          movement, and failed or declined transactions.
        - amount is the transaction amount only — never a balance, limit, or reference
          number.
        - merchant is the short human-readable payee or payer name.
        - When isTransaction is false, omit every other field.

        SMS: "Rs.500.00 debited from a/c XX1234 to VPA merchant@okbank on 12-01-26. Avl Bal Rs.9,500.00"
        JSON: {"isTransaction": true, "type": "DEBIT", "amount": 500.00, "merchant": "merchant@okbank", "accountLast4": "1234"}

        SMS: "You have received Rs.2,000 from John Doe via UPI. Ref No 123456789012."
        JSON: {"isTransaction": true, "type": "CREDIT", "amount": 2000, "merchant": "John Doe", "accountLast4": null}

        SMS: "Your OTP for login is 445599. Valid for 10 minutes. Do not share with anyone."
        JSON: {"isTransaction": false}

        SMS: "Your card XX9876 has a credit limit of Rs.2,00,000. Enjoy exclusive offers!"
        JSON: {"isTransaction": false}

        SMS: "Transaction of Rs.1,200 at Amazon was DECLINED due to insufficient balance."
        JSON: {"isTransaction": false}

        SMS: "${body.replace("\"", "'").take(MAX_INPUT_LENGTH)}"
        JSON:
    """.trimIndent()

    private companion object {
        const val TAG = "LlmTransactionExtractor"
        const val MAX_INPUT_LENGTH = 500
    }
}

/**
 * Pure parsing/validation of the model's raw text response, split out from
 * [LlmTransactionExtractor] so it can be unit-tested against canned strings without
 * constructing an [LlmEngine] (which needs a real on-device model and Android context).
 * Every field is validated against sane bounds before use — the model output is treated
 * as untrusted input, same as any other external data source, never as a value safe to
 * persist verbatim.
 */
internal object LlmTransactionJsonParser {
    private const val HANDLER_NAME = "LLM_FALLBACK"
    private const val MAX_SANE_AMOUNT = 1_00_00_000.0
    private const val MAX_MERCHANT_LENGTH = 80
    // Below every deterministic per-bank handler's typical confidence, and low enough to
    // keep isReviewed=false meaningful, but still above the pipeline's own LOW_CONFIDENCE
    // validator threshold — that validator never runs on this draft (it's built outside
    // Pipeline.process), so this only affects display/sorting.
    private const val LLM_CONFIDENCE = 55

    fun parse(rawResponse: String): TransactionDraft? {
        val json = extractJsonObject(rawResponse) ?: return null
        return parseDraft(json)
    }

    fun parseDraft(json: JSONObject): TransactionDraft? {
        if (!json.optBoolean("isTransaction", false)) return null

        val typeRaw = json.optString("type", "")
        val type = when (typeRaw.uppercase()) {
            "DEBIT" -> TransactionType.DEBIT
            "CREDIT" -> TransactionType.CREDIT
            else -> return null
        }

        val amount = json.optDouble("amount", -1.0)
        if (amount.isNaN() || amount <= 0.0 || amount > MAX_SANE_AMOUNT) return null

        val merchant = json.optString("merchant", "").trim().take(MAX_MERCHANT_LENGTH)
        if (merchant.isBlank()) return null

        val accountLast4 = json.optString("accountLast4", "").trim()
            .takeIf { it.length == 4 && it.all(Char::isDigit) }

        return TransactionDraft(
            intent = if (type == TransactionType.DEBIT) TransactionIntent.MONEY_DEBIT else TransactionIntent.MONEY_CREDIT,
            amount = amount,
            type = type,
            merchant = merchant,
            date = 0L,
            reference = "",
            accountLast4 = accountLast4,
            confidence = LLM_CONFIDENCE,
            handlerName = HANDLER_NAME,
            trust = TrustLevel.UNKNOWN,
        )
    }

    /** The model is instructed to answer with nothing but JSON, but is not trusted to
     *  follow that perfectly — this pulls out the first balanced-looking `{...}` span
     *  rather than assuming the whole response is clean JSON. */
    fun extractJsonObject(text: String): JSONObject? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start == -1 || end == -1 || end < start) return null
        return runCatching { JSONObject(text.substring(start, end + 1)) }.getOrNull()
    }
}
