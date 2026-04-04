package com.fynnix.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.fynnix.app.data.local.dao.*
import com.fynnix.app.data.local.entity.*

@Database(
    entities = [
        Account::class,
        Category::class,
        Transaction::class,
        MerchantCategoryOverride::class,
        AutoCategoriseRule::class,
        Budget::class,
        UserConfig::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun merchantCategoryOverrideDao(): MerchantCategoryOverrideDao
    abstract fun autoCategoriseRuleDao(): AutoCategoriseRuleDao
    abstract fun budgetDao(): BudgetDao
    abstract fun userConfigDao(): UserConfigDao
}