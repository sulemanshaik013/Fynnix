package com.fynnix.app.features.categories.data

import android.util.Log
import com.fynnix.app.core.utils.RuleCondition
import com.fynnix.app.core.utils.RuleField
import com.fynnix.app.features.budgets.data.BudgetDao
import com.fynnix.app.features.budgets.data.BudgetEntity
import com.fynnix.app.features.categories.domain.CategoryKeywords
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
            )
            categoryDao.insertAll(categories)
            categoryDao.seedUncategorizedCategory()

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
        val categoryIdByName = mapOf(
            "Food" to 1L, "Transport" to 2L, "Shopping" to 3L, "Bills" to 4L,
            "Entertainment" to 5L, "Health" to 6L, "Income" to 7L,
        )

        CategoryKeywords.GROUPS.forEachIndexed { groupIdx, group ->
            val categoryId = categoryIdByName[group.categoryName] ?: return@forEachIndexed
            val base = (groupIdx + 1) * 100
            group.keywords.forEachIndexed { idx, keyword ->
                ruleDao.insertRule(
                    AutoCategoriseRule(
                        ruleName   = keyword,
                        field      = RuleField.MERCHANT,
                        condition  = RuleCondition.CONTAINS,
                        value      = keyword,
                        categoryId = categoryId,
                        priority   = base + (idx * 10),
                    )
                )
            }
        }
    }
}
