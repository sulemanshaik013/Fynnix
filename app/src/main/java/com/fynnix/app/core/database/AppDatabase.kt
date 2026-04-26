package com.fynnix.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.fynnix.app.features.accounts.data.AccountDao
import com.fynnix.app.features.accounts.data.AccountEntity
import com.fynnix.app.features.budgets.data.BudgetDao
import com.fynnix.app.features.budgets.data.BudgetEntity
import com.fynnix.app.features.categories.data.*
import com.fynnix.app.features.transactions.data.FailedTransactionDao
import com.fynnix.app.features.transactions.data.FailedTransactionEntity
import com.fynnix.app.features.transactions.data.TransactionDao
import com.fynnix.app.features.transactions.data.TransactionEntity

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        MerchantCategoryOverride::class,
        AutoCategoriseRule::class,
        BudgetEntity::class,
        FailedTransactionEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(Converters::class,DateTimeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao

    abstract fun categoryDao(): CategoryDao

    abstract fun transactionDao(): TransactionDao

    abstract fun merchantCategoryOverrideDao(): MerchantCategoryOverrideDao

    abstract fun autoCategoriseRuleDao(): AutoCategoriseRuleDao

    abstract fun budgetDao(): BudgetDao

    abstract fun failedTransactionDao(): FailedTransactionDao
}
