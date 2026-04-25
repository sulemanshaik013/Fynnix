package com.fynnix.app.core.parser.contract

import com.fynnix.app.core.parser.NormalizedSms
import com.fynnix.app.core.parser.TransactionDraft

/**
 * Single rubric-style scorer. The default implementation lives in
 * `scoring.ConfidenceEngine`; tests can swap a stub to drive specific
 * acceptance/rejection paths through Pipeline.
 */
interface ConfidenceScorer {
    fun score(sms: NormalizedSms, draft: TransactionDraft): Int
}
