package com.fynnix.app.features.categories.domain

import com.fynnix.app.core.utils.RuleCondition
import com.fynnix.app.core.utils.RuleField
import com.fynnix.app.features.categories.data.AutoCategoriseRuleDao
import com.fynnix.app.features.categories.data.CategoryDao
import com.fynnix.app.features.categories.data.MerchantCategoryOverrideDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Composite classifier that resolves category in three layers:
 *
 * 1. User merchant overrides (exact key match, highest precedence)
 * 2. DB auto-categorise rules ordered by priority ASC (user-editable)
 * 3. Hardcoded keyword fallback (covers fresh installs before DB seeding completes)
 */
@Singleton
class ChainedCategoryClassifier @Inject constructor(
    private val overrideDao: MerchantCategoryOverrideDao,
    private val ruleDao: AutoCategoriseRuleDao,
    private val categoryDao: CategoryDao,
) : CategoryClassifier {

    override suspend fun classify(merchant: String, amount: Double): Long {
        val m = merchant.lowercase().trim()
        if (m.isEmpty()) return 0L

        // Layer 1: explicit user override
        overrideDao.getOverrideByMerchantKey(m)?.let { return it.categoryId }

        // Layer 2: DB rules (priority-ordered)
        for (rule in ruleDao.getAllList()) {
            if (rule.field != RuleField.MERCHANT) continue
            val v = rule.value.lowercase()
            val hit = when (rule.condition) {
                RuleCondition.CONTAINS    -> CategoryKeywords.matches(m, v)
                RuleCondition.EQUALS      -> m == v
                RuleCondition.STARTS_WITH -> m.startsWith(v)
            }
            if (hit) return rule.categoryId
        }

        // Layer 3: hardcoded keyword fallback (safety net for fresh installs)
        val fallbackName = keywordFallback(m) ?: return 0L
        return categoryDao.findIdByName(fallbackName) ?: 0L
    }

    private fun keywordFallback(merchant: String): String? =
        CategoryKeywords.GROUPS
            .firstOrNull { group -> group.keywords.any { CategoryKeywords.matches(merchant, it) } }
            ?.categoryName
}
