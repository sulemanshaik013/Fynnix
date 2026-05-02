package com.fynnix.app.features.onboarding.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fynnix.app.core.data.UserPreferences
import com.fynnix.app.core.utils.PeriodType
import com.fynnix.app.core.utils.launchCatching
import com.fynnix.app.features.budgets.data.BudgetDao
import com.fynnix.app.features.budgets.data.BudgetEntity
import com.fynnix.app.features.categories.data.CategoryDao
import com.fynnix.app.features.categories.data.CategoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val categoryDao: CategoryDao,
    private val budgetDao: BudgetDao,
) : ViewModel() {

    private val _categories = MutableStateFlow<List<CategoryEntity>>(emptyList())
    val categories: StateFlow<List<CategoryEntity>> = _categories.asStateFlow()

    /** In-memory edits to budget limits; keyed by categoryId. Persisted on saveData(). */
    private val _budgetEdits = MutableStateFlow<Map<Long, Double>>(emptyMap())
    val budgetEdits: StateFlow<Map<Long, Double>> = _budgetEdits.asStateFlow()

    fun loadCategories() {
        viewModelScope.launchCatching(TAG) {
            val cats = categoryDao.getAll().filter { it.id != 0L }
            _categories.value = cats

            // Prime the edit map with whatever is already persisted in BudgetEntity.
            val persisted = buildMap<Long, Double> {
                cats.forEach { cat ->
                    val b = budgetDao.getBudgetByCategoryId(cat.id)
                    if (b != null) put(cat.id, b.limitAmount)
                }
            }
            _budgetEdits.value = persisted
        }
    }

    /** Persist user profile + edited budgets. Does NOT mark onboarding complete —
     *  AllSetViewModel does that after the SMS sync run. */
    fun saveData(name: String, income: Double) {
        viewModelScope.launchCatching(TAG) {
            userPreferences.saveUserName(name)
            userPreferences.saveUserIncome(income)

            _budgetEdits.value.forEach { (categoryId, limit) ->
                val existing = budgetDao.getBudgetByCategoryId(categoryId)
                if (existing != null) {
                    budgetDao.updateBudget(existing.copy(limitAmount = limit))
                } else {
                    budgetDao.insertBudget(
                        BudgetEntity(categoryId = categoryId, limitAmount = limit, periodType = PeriodType.MONTHLY)
                    )
                }
            }
        }
    }

    fun updateBudget(id: Long, newBudget: Double) {
        _budgetEdits.update { it + (id to newBudget) }
    }

    private companion object {
        const val TAG = "OnboardingViewModel"
    }
}
