package com.fynnix.app.core.parser.extractor

import com.fynnix.app.core.parser.contract.Extractor
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extracts a merchant string from real Indian bank/wallet SMS shapes.
 *
 * Strategy: run the patterns against the **original** body (not the cleaned
 * body) so casing is preserved, then:
 *   - resolve numeric UPI VPA prefixes to their payment provider name
 *     (e.g. "9876543210@paytm" → "Paytm")
 *   - drop the bank-name prefix tokens (HDFC / ICICI / SBI / Axis / Kotak)
 *   - drop product nouns (bank, card, a/c, ref, upi, info)
 *   - strip legal suffixes (Pvt Ltd, etc.)
 *   - title-case ALL-CAPS strings, otherwise keep as-is
 *   - reject empty / pure-noise candidates and try the next pattern
 */
@Singleton
class MerchantExtractor @Inject constructor() : Extractor<String> {

    override fun extract(text: String): String {
        val lower = text.lowercase()
        if (lower.contains("atm") && (lower.contains("withdrawn") || lower.contains("debited"))) return "ATM Withdrawal"
        if (lower.contains("neft")) return "NEFT Transfer"
        if (lower.contains("imps")) return "IMPS Transfer"
        if (lower.contains("rtgs")) return "RTGS Transfer"
        if (lower.contains("salary") || lower.contains("payroll")) return "Salary"

        for (pattern in PATTERNS) {
            val match = pattern.find(text) ?: continue
            val raw = match.groupValues.firstOrNull { it.isNotBlank() && match.groupValues.indexOf(it) > 0 }
                ?: match.groupValues.getOrNull(1)
                ?: continue
            val cleaned = clean(raw.trim()) ?: continue
            return cleaned
        }
        return "Transaction"
    }

    private fun clean(input: String): String? {
        // If the candidate is a UPI VPA (contains '@'), handle it specifically.
        if (input.contains('@')) return resolveVpa(input)

        var s = input
            .replace('.', ' ')
            .replace('_', ' ')
            .replace('/', ' ')
            .replace('-', ' ')
            .replace(Regex("[^A-Za-z0-9 &]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (s.isBlank()) return null

        val tokens = s.split(' ').filter { it.isNotBlank() }
        val kept = tokens
            .filter { it.lowercase() !in NOISE_TOKENS }
            .filter { !PURE_NUMBER.matches(it) }
            .filter { it.length >= 2 || it.equals("co", true) }
        if (kept.isEmpty()) return null

        var joined = kept.joinToString(" ")
        for (suffix in LEGAL_SUFFIXES) {
            if (joined.endsWith(suffix, ignoreCase = true)) {
                joined = joined.dropLast(suffix.length).trim()
            }
        }
        if (joined.isBlank() || joined.length !in 2..40) return null
        if (joined.lowercase() in NOISE_TOKENS) return null

        val needsTitleCase = joined == joined.uppercase() || joined == joined.lowercase()
        return if (needsTitleCase) joined.titleCase() else joined
    }

    /**
     * Resolves a UPI VPA to a merchant name.
     *
     * - Named prefix  (e.g. "zomato@hdfcbank")  → clean the local part → "Zomato"
     * - Numeric prefix (e.g. "9876543210@paytm") → map suffix to provider name
     *   because a phone number is meaningless to the user.
     */
    private fun resolveVpa(vpa: String): String? {
        val local  = vpa.substringBefore('@').trim()
        val suffix = vpa.substringAfter('@').lowercase().trim()

        // If local part is a pure phone number / all-digit, use provider name instead.
        if (local.replace(Regex("[+ ]"), "").all { it.isDigit() } && local.isNotBlank()) {
            return VPA_SUFFIX_TO_PROVIDER[suffix]
                ?: suffix.split('.').first().titleCase().takeIf { it.length >= 2 }
                ?: "UPI Payment"
        }

        // Named prefix — clean it the normal way.
        return clean(local)
    }

    private fun String.titleCase(): String =
        split(' ').joinToString(" ") { word ->
            if (word.isBlank()) word
            else word.lowercase().replaceFirstChar { it.titlecase(Locale.getDefault()) }
        }

    private companion object {

        /** Maps common UPI handle suffixes to a human-readable provider name. */
        val VPA_SUFFIX_TO_PROVIDER = mapOf(
            "paytm"      to "Paytm",
            "ybl"        to "PhonePe",
            "ibl"        to "PhonePe",
            "axl"        to "PhonePe",
            "okaxis"     to "Google Pay",
            "okicici"    to "Google Pay",
            "okhdfcbank" to "Google Pay",
            "oksbi"      to "Google Pay",
            "apl"        to "Amazon Pay",
            "yapl"       to "Amazon Pay",
            "icici"      to "iMobile Pay",
            "hdfcbank"   to "HDFC Bank",
            "sbi"        to "YONO SBI",
            "upi"        to "BHIM",
            "bhim"       to "BHIM",
        )

        // Order matters — more specific patterns first.
        val PATTERNS: List<Regex> = listOf(
            // "e-mandate towards NETFLIX INDIA for", "autopay for SPOTIFY", "SI in favour of X" —
            // mandate/autopay wording names the merchant right after the mandate keyword, which
            // is a more reliable anchor than the generic "to X on" pattern below and must be
            // tried first so it isn't shadowed by a looser match.
            Regex(
                "(?:e-mandate|mandate|autopay|auto-debit|auto debit|standing instruction|\\bsi\\b)" +
                    "\\s+(?:for|towards|in favou?r of)\\s+([A-Za-z][A-Za-z0-9 .&\\-]{2,40}?)" +
                    "(?:\\s+(?:for|has|is|umn|ref|on)|[.,;]|\\s*\$)",
                RegexOption.IGNORE_CASE,
            ),
            // UPI VPA: "to xyz@hdfcbank", "VPA xyz@bank"
            Regex("(?:to|at|via|vpa)\\s+([A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+)", RegexOption.IGNORE_CASE),
            // "UPI/P2A/MERCHANT NAME/..." (Axis / Kotak structured ref)
            Regex("UPI[\\/\\\\][A-Z0-9]{2,4}[\\/\\\\]([A-Za-z][A-Za-z0-9 .&\\-]{2,40}?)[\\/\\\\]", RegexOption.IGNORE_CASE),
            // "spent ... at MERCHANT on dd-MMM-yy"
            Regex("\\bat\\s+([A-Za-z][A-Za-z0-9 .&\\-]{2,40}?)\\s+on\\b", RegexOption.IGNORE_CASE),
            // "to MERCHANT on dd"
            Regex("\\bto\\s+([A-Za-z][A-Za-z0-9 .&\\-]{2,40}?)\\s+on\\b", RegexOption.IGNORE_CASE),
            // "by transfer to MERCHANT [on|.|,|;]"
            Regex("by\\s+transfer\\s+to\\s+([A-Za-z][A-Za-z0-9 .&\\-]{2,40}?)(?:\\s+on|[.,;]|\\s*\$)", RegexOption.IGNORE_CASE),
            // "paid to MERCHANT [Ref|.|,|;]"
            Regex("paid\\s+to\\s+([A-Za-z][A-Za-z0-9 .&\\-]{2,40}?)(?:\\s+(?:ref|on|via|inr)|[.,;]|\\s*\$)", RegexOption.IGNORE_CASE),
            // "Info: MERCHANT" (HDFC POS)
            Regex("\\bInfo[:\\s]+([A-Za-z][A-Za-z0-9 .&\\-]{2,40}?)(?:[.,;]|\\s*\$)", RegexOption.IGNORE_CASE),
            // Generic "to MERCHANT" terminating at punctuation or keywords
            Regex("\\bto\\s+([A-Za-z][A-Za-z0-9 .&\\-]{2,40}?)(?:\\s+(?:ref|via|inr|upi)|[.,;]|\\s*\$)", RegexOption.IGNORE_CASE),
            Regex("\\bat\\s+([A-Za-z][A-Za-z0-9 .&\\-]{2,40}?)(?:\\s+(?:ref|via|inr|upi)|[.,;]|\\s*\$)", RegexOption.IGNORE_CASE),
            // "from MERCHANT" (credits)
            Regex("\\bfrom\\s+([A-Za-z][A-Za-z0-9 .&\\-]{2,40}?)(?:\\s+(?:ref|via|inr|upi|on)|[.,;]|\\s*\$)", RegexOption.IGNORE_CASE),
        )

        val LEGAL_SUFFIXES = listOf(
            " pvt ltd", " private limited", " limited", " ltd", " inc",
            " llp", " llc", " corp", " co", " services", " seller services",
            " india",
        )

        val NOISE_TOKENS = setOf(
            "hdfc", "icici", "sbi", "axis", "kotak", "idfc", "yes", "indus",
            "bank", "card", "a", "ac", "acct", "account", "no", "ref",
            "upi", "neft", "imps", "rtgs", "info", "txn", "mr", "mrs",
            "ms", "the", "and", "for", "via", "on", "inr", "rs", "p2a",
            "p2m", "cust", "customer", "dear",
        )

        val PURE_NUMBER = Regex("^[\\d+\\s]+$")
    }
}
