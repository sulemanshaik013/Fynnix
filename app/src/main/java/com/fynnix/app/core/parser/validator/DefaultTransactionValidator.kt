package com.fynnix.app.core.parser.validator

import com.fynnix.app.core.parser.ParseResult
import com.fynnix.app.core.parser.RejectionReason
import com.fynnix.app.core.parser.TransactionDraft
import com.fynnix.app.core.parser.contract.TransactionValidator
import com.fynnix.app.core.parser.scoring.ConfidenceEngine
import javax.inject.Inject
import javax.inject.Singleton

/** Production rule: prefer false negatives over false positives. */
@Singleton
class DefaultTransactionValidator @Inject constructor() : TransactionValidator {

    override fun validate(draft: TransactionDraft): ParseResult {
        if (draft.amount <= 0)
            return ParseResult.Rejected(RejectionReason.MISSING_AMOUNT, draft.handlerName)
        if (!draft.intent.isFinancial)
            return ParseResult.Rejected(RejectionReason.UNKNOWN_INTENT, draft.handlerName)
        if (draft.confidence < ConfidenceEngine.ACCEPT_THRESHOLD)
            return ParseResult.Rejected(
                RejectionReason.LOW_CONFIDENCE,
                draft.handlerName,
                detail = "score=${draft.confidence}"
            )
        return ParseResult.Success(draft)
    }
}
