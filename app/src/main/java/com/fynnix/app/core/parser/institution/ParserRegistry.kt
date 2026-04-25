package com.fynnix.app.core.parser.institution

import com.fynnix.app.core.parser.contract.InstitutionParser
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sorts the injected institution parsers by priority once, then resolves
 * the first match for a given sender. Isolating this from the Pipeline
 * keeps Pipeline focused on stage orchestration.
 */
@Singleton
class ParserRegistry @Inject constructor(
    parsers: Set<@JvmSuppressWildcards InstitutionParser>,
) {
    private val ordered: List<InstitutionParser> = parsers.sortedByDescending { it.priority }

    /** First parser whose `canHandle` claims the sender (priority order). */
    fun find(senderUpper: String): InstitutionParser? =
        ordered.firstOrNull { it.canHandle(senderUpper) }
}
