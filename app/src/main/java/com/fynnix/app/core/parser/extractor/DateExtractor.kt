package com.fynnix.app.core.parser.extractor

import com.fynnix.app.core.parser.contract.Extractor
import com.fynnix.app.core.utils.AppConstants
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extracts the bank-event timestamp from raw SMS body. Tries every common
 * Indian bank format. Returns null if none match — caller falls back to
 * the SMS-arrival timestamp.
 */
@Singleton
class DateExtractor @Inject constructor() : Extractor<Long> {

    /** Each entry pairs a regex with a SimpleDateFormat pattern using the regex's two captures. */
    private data class FormatRule(val regex: Regex, val sdfPattern: String)

    private val rules: List<FormatRule> = listOf(
        // 03/12/2024 14:30  or 03-12-2024 14:30
        FormatRule(Regex("(\\d{1,2}[-/]\\d{1,2}[-/]\\d{4})\\s+(\\d{1,2}:\\d{2}(?::\\d{2})?)"), "dd/MM/yyyy HH:mm:ss"),
        // 03/12/24 14:30
        FormatRule(Regex("(\\d{1,2}[-/]\\d{1,2}[-/]\\d{2})\\s+(\\d{1,2}:\\d{2}(?::\\d{2})?)"), "dd/MM/yy HH:mm:ss"),
        // 03-Dec-24 14:30  or 03 Dec 24 14:30
        FormatRule(Regex("(\\d{1,2}[\\s-][A-Za-z]{3}[\\s-]\\d{2})\\s+(\\d{1,2}:\\d{2}(?::\\d{2})?)"), "dd MMM yy HH:mm:ss"),
        // 03-Dec-2024 14:30
        FormatRule(Regex("(\\d{1,2}[\\s-][A-Za-z]{3}[\\s-]\\d{4})\\s+(\\d{1,2}:\\d{2}(?::\\d{2})?)"), "dd MMM yyyy HH:mm:ss"),
        // 03-Dec-2024 (no time)
        FormatRule(Regex("(\\d{1,2}[\\s-][A-Za-z]{3}[\\s-]\\d{4})()"), "dd MMM yyyy"),
        // 03-Dec-24 (no time)
        FormatRule(Regex("(\\d{1,2}[\\s-][A-Za-z]{3}[\\s-]\\d{2})()"), "dd MMM yy"),
        // 03/12/2024 (no time)
        FormatRule(Regex("(\\d{1,2}[-/]\\d{1,2}[-/]\\d{4})()"), "dd/MM/yyyy"),
        // 03/12/24 (no time)
        FormatRule(Regex("(\\d{1,2}[-/]\\d{1,2}[-/]\\d{2})()"), "dd/MM/yy"),
        // ISO 2024-12-03[ 14:30]
        FormatRule(Regex("(\\d{4}-\\d{2}-\\d{2})(?:[\\sT](\\d{1,2}:\\d{2}(?::\\d{2})?))?"), "yyyy-MM-dd HH:mm:ss"),
        // 03 Dec, 14:30  (no year — assume current year)
        FormatRule(Regex("(\\d{1,2}\\s+[A-Za-z]{3}),?\\s+(\\d{1,2}:\\d{2}(?::\\d{2})?)"), "dd MMM HH:mm:ss"),
    )

    override fun extract(text: String): Long? {
        for (rule in rules) {
            val match = rule.regex.find(text) ?: continue
            val datePart = match.groupValues[1].replace('-', ' ').replace('/', '/').trim()
            val timePart = match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() } ?: ""

            // Reconstruct strings the SDF can parse, normalising delimiters.
            val (datePartFinal, timePartFinal, sdfFinal) = normalise(datePart, timePart, rule.sdfPattern)

            val combined = if (timePartFinal.isBlank()) datePartFinal else "$datePartFinal $timePartFinal"
            val parsed = parse(combined, sdfFinal) ?: continue
            // If the format had no year (e.g. "dd MMM"), SimpleDateFormat defaults to 1970.
            // Detect and re-anchor to current year. If that produces a future date, roll back one year.
            val anchored = anchorToCurrentYearIfMissing(parsed, rule.sdfPattern)

            // A bank transaction is never future-dated. None of these regexes are anchored
            // to a "this is definitely the transaction date" context the way AmountExtractor
            // strips balance/account phrases first — an unrelated digit run elsewhere in the
            // body (a card expiry, an offer's validity window, a reference number that
            // happens to contain delimiters) can match one of these patterns and produce a
            // date years away. Skip an implausible match and keep trying the remaining rules
            // instead of returning it outright; if nothing plausible is found the caller
            // falls back to the SMS arrival timestamp, which is always correct.
            if (!isPlausible(anchored)) continue
            return anchored
        }
        return null
    }

    private fun isPlausible(dateMillis: Long): Boolean =
        dateMillis <= System.currentTimeMillis() + AppConstants.DATE_FUTURE_GRACE_MS

    private fun normalise(datePart: String, timePart: String, sdf: String): Triple<String, String, String> {
        // Force date delimiter to space (we standardised regex to allow `-`/`/`/space).
        val date = datePart.replace(Regex("[/\\-]"), " ")
        val time = timePart
        // Adjust SDF pattern to spaces too.
        val pattern = sdf.replace("/", " ").replace("-", " ").trim()
        // If the rule had no time portion, drop the time component from the pattern.
        val finalPattern = if (time.isBlank()) pattern.substringBefore(" HH").trim() else pattern
        return Triple(date, time, finalPattern)
    }

    private fun parse(combined: String, pattern: String): Long? {
        return try {
            // Try with seconds first, fall back without seconds.
            val variants = listOf(pattern, pattern.removeSuffix(":ss"))
            for (p in variants) {
                runCatching {
                    SimpleDateFormat(p, Locale.ENGLISH).apply { isLenient = false }
                        .parse(combined)?.time
                }.getOrNull()?.let { return it }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun anchorToCurrentYearIfMissing(parsed: Long, sdf: String): Long {
        if (sdf.contains("yyyy") || sdf.contains("yy")) return parsed
        val cal = Calendar.getInstance().apply { timeInMillis = parsed }
        val nowYear = Calendar.getInstance().get(Calendar.YEAR)
        cal.set(Calendar.YEAR, nowYear)
        if (cal.timeInMillis > System.currentTimeMillis() + ROLLBACK_GRACE) {
            cal.set(Calendar.YEAR, nowYear - 1)
        }
        return cal.timeInMillis
    }

    private companion object {
        val ROLLBACK_GRACE = AppConstants.DATE_FUTURE_GRACE_MS  // 7 days
    }
}
