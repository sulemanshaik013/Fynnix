package com.fynnix.app.features.categories.domain

/**
 * Classifies a transaction into a category ID.
 * Returns 0L (Uncategorized) when no rule matches.
 *
 * Resolution order: user merchant overrides → DB rules (priority ASC) → keyword fallback.
 */
interface CategoryClassifier {
    suspend fun classify(merchant: String, amount: Double = 0.0): Long
}
