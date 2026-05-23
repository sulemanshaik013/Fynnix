package com.fynnix.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.fynnix.app.features.accounts.data.AccountDao
import com.fynnix.app.features.accounts.data.AccountEntity
import com.fynnix.app.features.budgets.data.BudgetDao
import com.fynnix.app.features.budgets.data.BudgetEntity
import com.fynnix.app.features.categories.data.*
import com.fynnix.app.features.reminders.data.BillReminderDao
import com.fynnix.app.features.reminders.data.BillReminderEntity
import com.fynnix.app.features.subscriptions.data.SubscriptionMandateDao
import com.fynnix.app.features.subscriptions.data.SubscriptionMandateEntity
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
        SubscriptionMandateEntity::class,
        BillReminderEntity::class,
    ],
    version = 7,
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

    abstract fun subscriptionMandateDao(): SubscriptionMandateDao

    abstract fun billReminderDao(): BillReminderDao

    companion object {
        /** Single source of truth for the on-disk file name — [DatabaseModule] builds the
         *  Room instance with this, and backup/restore locates the same file with it. */
        const val DB_NAME = "fynnix.db"
    }
}
