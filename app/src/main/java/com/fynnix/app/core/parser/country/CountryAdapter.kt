package com.fynnix.app.core.parser.country

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Country-specific sender hygiene. Today only India is implemented — strips
 * the 2-char DLT prefix (e.g. `JD-HDFCBK` → `HDFCBK`). Add USA/SG/AE here
 * as additional countries come online.
 */
@Singleton
class CountryAdapter @Inject constructor() {

    fun adaptSender(rawSender: String, simCountry: String): String {
        return when (simCountry.uppercase()) {
            "IN" -> stripIndiaDltPrefix(rawSender)
            else -> rawSender.trim()
        }
    }

    private fun stripIndiaDltPrefix(sender: String): String {
        val s = sender.trim()
        if (s.length > 3 && (s[2] == '-' || s[2] == '_')) return s.substring(3)
        return s
    }
}
