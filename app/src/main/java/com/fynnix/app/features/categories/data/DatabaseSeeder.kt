package com.fynnix.app.features.categories.data

import android.util.Log
import com.fynnix.app.features.budgets.data.BudgetDao
import com.fynnix.app.features.budgets.data.BudgetEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseSeeder @Inject constructor(
    private val categoryDao: CategoryDao,
    private val budgetDao: BudgetDao,
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

            // Seed default monthly budgets. Income and Uncategorized intentionally
            // have no budget limit — they are not spending categories.
            val defaultBudgets = mapOf(
                1L to 8_000.0,  // Food
                2L to 4_000.0,  // Transport
                3L to 8_000.0,  // Shopping
                4L to 3_000.0,  // Bills
                5L to 2_000.0,  // Entertainment
                6L to 2_000.0,  // Health
            )
            defaultBudgets.forEach { (categoryId, limit) ->
                if (budgetDao.getBudgetByCategoryId(categoryId) == null) {
                    budgetDao.insertBudget(BudgetEntity(categoryId = categoryId, limitAmount = limit))
                }
            }
        }
    }
}
