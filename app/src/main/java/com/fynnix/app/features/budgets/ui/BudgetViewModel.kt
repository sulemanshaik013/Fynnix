package com.fynnix.app.features.budgets.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fynnix.app.core.analytics.TimeRanges
import com.fynnix.app.core.utils.PeriodType
import com.fynnix.app.core.utils.launchCatching
import com.fynnix.app.features.budgets.data.BudgetDao
import com.fynnix.app.features.budgets.data.BudgetEntity
import com.fynnix.app.features.categories.data.CategoryDao
import com.fynnix.app.features.categories.data.CategoryEntity
import com.fynnix.app.features.transactions.data.TransactionDao
import com.fynnix.app.features.transactions.data.toUiMeta
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class BudgetUiModel(
    val categoryName: String,
    val spentAmount: Double,
    val limitAmount: Double,
)

data class CategoryBudgetEditUiModel(
    val id: Long,
    val name: String,
    val icon: ImageVector,
    val color: Color,
    val spent: Double,
    val budgetLimit: Double,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val categoryDao: CategoryDao,
    private val budgetDao: BudgetDao,
    private val transactionDao: TransactionDao,
) : ViewModel() {

    val categoriesWithBudget = TimeRanges.ticking(TimeRanges::thisMonth)
        .flatMapLatest { range ->
            combine(
                categoryDao.getAllFlow(),
                budgetDao.getAllBudgets(),
                transactionDao.categorySpendFlow(range.start, range.end),
            ) { categories: List<CategoryEntity>, budgets, spendRows ->
                val budgetByCat = budgets.associate { it.categoryId to it.limitAmount }
                val spendByCat  = spendRows.associate { it.categoryId to it.total }
                categories
                    .filter { it.id != 0L }
                    .sortedBy { it.sortOrder }
                    .map { cat ->
                        val (name, icon, color) = cat.toUiMeta()
                        CategoryBudgetEditUiModel(
                            id          = cat.id,
                            name        = name,
                            icon        = icon,
                            color       = color,
                            spent       = spendByCat[cat.id]  ?: 0.0,
                            budgetLimit = budgetByCat[cat.id] ?: 0.0,
                        )
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateBudget(categoryId: Long, newLimit: Double) {
        viewModelScope.launchCatching(TAG) {
            val existing = budgetDao.getBudgetByCategoryId(categoryId)
            if (existing != null) {
                budgetDao.updateBudget(existing.copy(limitAmount = newLimit))
            } else {
                budgetDao.insertBudget(
                    BudgetEntity(categoryId = categoryId, limitAmount = newLimit, periodType = PeriodType.MONTHLY)
                )
            }
        }
    }

    fun addCategory(name: String) {
        viewModelScope.launchCatching(TAG) {
            val trimmed = name.trim()
            if (trimmed.isBlank()) return@launchCatching
            val existing = categoryDao.findIdByName(trimmed)
            if (existing != null) return@launchCatching
            val nextSort = (categoryDao.getAll().maxOfOrNull { it.sortOrder } ?: 0) + 1
            categoryDao.insert(
                CategoryEntity(
                    name      = trimmed,
                    icon      = "help",
                    colorHex  = "#9CA3AF",
                    isDefault = false,
                    sortOrder = nextSort,
                )
            )
        }
    }

    fun removeCategory(id: Long) {
        viewModelScope.launchCatching(TAG) {
            val cat = categoryDao.getCategoryById(id) ?: return@launchCatching
            if (cat.isDefault) return@launchCatching
            categoryDao.deleteCategory(cat)
        }
    }

    private companion object {
        const val TAG = "BudgetViewModel"
    }
}
