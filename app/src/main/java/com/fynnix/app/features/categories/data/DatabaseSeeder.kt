package com.fynnix.app.features.categories.data

import android.util.Log
import com.fynnix.app.core.utils.RuleCondition
import com.fynnix.app.core.utils.RuleField
import com.fynnix.app.features.budgets.data.BudgetDao
import com.fynnix.app.features.budgets.data.BudgetEntity
import com.fynnix.app.features.categories.domain.ChainedCategoryClassifier
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseSeeder @Inject constructor(
    private val categoryDao: CategoryDao,
    private val budgetDao: BudgetDao,
    private val ruleDao: AutoCategoriseRuleDao,
) {
    suspend fun seedIfEmpty() {
        if (categoryDao.getCount() == 0) {
            Log.d("DatabaseSeeder", "Seeding categories and default budgets...")

            val categories = listOf(
                CategoryEntity(id = 1L, name = "Food",          icon = "restaurant",       colorHex = "#FF5722", isDefault = true, sortOrder = 1),
                CategoryEntity(id = 2L, name = "Transport",     icon = "directions_car",   colorHex = "#2196F3", isDefault = true, sortOrder = 2),
                CategoryEntity(id = 3L, name = "Shopping",      icon = "shopping_bag",     colorHex = "#9C27B0", isDefault = true, sortOrder = 3),
                CategoryEntity(id = 4L, name = "Bills",         icon = "receipt",          colorHex = "#4CAF50", isDefault = true, sortOrder = 4),
                CategoryEntity(id = 5L, name = "Entertainment", icon = "movie",            colorHex = "#E91E63", isDefault = true, sortOrder = 5),
                CategoryEntity(id = 6L, name = "Health",        icon = "medical_services", colorHex = "#F44336", isDefault = true, sortOrder = 6),
                CategoryEntity(id = 7L, name = "Income",        icon = "payments",         colorHex = "#00C853", isDefault = true, sortOrder = 7),
                CategoryEntity(id = 0L, name = "Uncategorized", icon = "help",             colorHex = "#9E9E9E", isDefault = true, sortOrder = 99),
            )
            categoryDao.insertAll(categories)

            val defaultBudgets = mapOf(
                1L to 8_000.0,
                2L to 4_000.0,
                3L to 8_000.0,
                4L to 3_000.0,
                5L to 2_000.0,
                6L to 2_000.0,
            )
            defaultBudgets.forEach { (categoryId, limit) ->
                if (budgetDao.getBudgetByCategoryId(categoryId) == null) {
                    budgetDao.insertBudget(BudgetEntity(categoryId = categoryId, limitAmount = limit))
                }
            }
        }

        if (ruleDao.getCount() == 0) {
            Log.d("DatabaseSeeder", "Seeding default auto-categorise rules...")
            seedDefaultRules()
        }
    }

    private suspend fun seedDefaultRules() {
        // (categoryId, keywords, groupPriority base)
        data class RuleGroup(val categoryId: Long, val keywords: List<String>, val base: Int)

        val groups = listOf(
            RuleGroup(1L, ChainedCategoryClassifier.FOOD,          100),
            RuleGroup(2L, ChainedCategoryClassifier.TRANSPORT,     200),
            RuleGroup(3L, ChainedCategoryClassifier.SHOPPING,      300),
            RuleGroup(4L, ChainedCategoryClassifier.BILLS,         400),
            RuleGroup(5L, ChainedCategoryClassifier.ENTERTAINMENT, 500),
            RuleGroup(6L, ChainedCategoryClassifier.HEALTH,        600),
            RuleGroup(7L, ChainedCategoryClassifier.INCOME,        700),
        )

        groups.forEach { group ->
            group.keywords.forEachIndexed { idx, keyword ->
                ruleDao.insertRule(
                    AutoCategoriseRule(
                        ruleName   = keyword,
                        field      = RuleField.MERCHANT,
                        condition  = RuleCondition.CONTAINS,
                        value      = keyword,
                        categoryId = group.categoryId,
                        priority   = group.base + (idx * 10),
                    )
                )
            }
        }
    }
}
