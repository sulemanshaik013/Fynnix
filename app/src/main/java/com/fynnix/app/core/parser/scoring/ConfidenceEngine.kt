package com.fynnix.app.core.parser.scoring

import com.fynnix.app.core.parser.NormalizedSms
import com.fynnix.app.core.parser.TransactionDraft
import com.fynnix.app.core.parser.contract.ConfidenceScorer
import com.fynnix.app.core.parser.filter.PromotionalFilter
import com.fynnix.app.core.utils.AppConstants
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rubric-style scorer (additive, auditable, easy to extend).
 *
 *   trusted sender         + trust.score (up to 40)
 *   amount found           +20
 *   financial intent       +25
 *   account / card found   +10
 *   reference found        +10
 *   suspicious URL         -30
 */
@Singleton
class ConfidenceEngine @Inject constructor(
    private val promotionalFilter: PromotionalFilter,
) : ConfidenceScorer {

    override fun score(sms: NormalizedSms, draft: TransactionDraft): Int {
        var s = 0
        s += draft.trust.score
        if (draft.amount > 0) s += 20
        if (draft.intent.isFinancial) s += 25
        if (!draft.accountLast4.isNullOrBlank()) s += 10
        if (draft.reference.isNotBlank()) s += 10
        if (promotionalFilter.hasSuspiciousUrl(sms.raw.body)) s -= 30
        return s.coerceIn(0, 100)
    }

    companion object {
        val ACCEPT_THRESHOLD = AppConstants.CONFIDENCE_THRESHOLD
    }
}
