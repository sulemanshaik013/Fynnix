package com.fynnix.app.core.parser.contract

import com.fynnix.app.core.parser.ParseResult
import com.fynnix.app.core.parser.TransactionDraft

/**
 * Final accept/reject gate. Receives a fully-scored draft and decides
 * whether it crosses the bar.
 *
 * Production rule (per design doc): prefer false negatives over false
 * positives — when in doubt, reject.
 */
interface TransactionValidator {
    fun validate(draft: TransactionDraft): ParseResult
}
