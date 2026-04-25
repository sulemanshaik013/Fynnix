package com.fynnix.app.core.di

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.fynnix.app.core.database.ALL_MIGRATIONS
import com.fynnix.app.core.database.AppDatabase
import com.fynnix.app.core.security.SecureKeyVault
import com.fynnix.app.features.accounts.data.AccountDao
import com.fynnix.app.features.budgets.data.BudgetDao
import com.fynnix.app.features.categories.data.AutoCategoriseRuleDao
import com.fynnix.app.features.categories.data.CategoryDao
import com.fynnix.app.features.categories.data.MerchantCategoryOverrideDao
import com.fynnix.app.features.transactions.data.FailedTransactionDao
import com.fynnix.app.features.transactions.data.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private const val TAG = "DatabaseModule"
    private const val DB_NAME = "fynnix.db"

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        keyVault: SecureKeyVault,
    ): AppDatabase {
        SQLiteDatabase.loadLibs(context)

        val passphrase = try {
            keyVault.getOrCreateDbPassphrase()
        } catch (e: Exception) {
            // Keystore unavailable (e.g. after a factory-reset of the secure element).
            // We cannot open the encrypted DB without the passphrase — fail loudly so
            // the crash reporter surfaces the issue, rather than silently creating an
            // unencrypted database that could contain sensitive data.
            Log.e(TAG, "Cannot retrieve DB passphrase from keystore", e)
            throw IllegalStateException(
                "Fynnix database key is unavailable. " +
                    "This can happen after a factory reset of the device keystore. " +
                    "Please reinstall the app to start fresh.",
                e
            )
        }

        val factory = SupportFactory(passphrase)

        return try {
            buildDatabase(context, factory)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open encrypted database", e)
            // Re-throw with a user-legible message; the app will crash with a visible
            // error dialog rather than a silent ANR. Callers (e.g. a global exception
            // handler) can intercept this to show a "restore from backup" screen.
            throw IllegalStateException(
                "Fynnix could not open the encrypted database. " +
                    "If you recently transferred your device or cleared storage, " +
                    "you may need to restore from a backup.",
                e
            )
        }
    }

    private fun buildDatabase(context: Context, factory: SupportFactory): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
            .openHelperFactory(factory)
            .addMigrations(*ALL_MIGRATIONS)
            .build()

    @Provides fun provideAccountDao(db: AppDatabase): AccountDao = db.accountDao()
    @Provides fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideTransactionDao(db: AppDatabase): TransactionDao = db.transactionDao()
    @Provides fun provideMerchantOverrideDao(db: AppDatabase): MerchantCategoryOverrideDao = db.merchantCategoryOverrideDao()
    @Provides fun provideAutoCategoriseRuleDao(db: AppDatabase): AutoCategoriseRuleDao = db.autoCategoriseRuleDao()
    @Provides fun provideBudgetDao(db: AppDatabase): BudgetDao = db.budgetDao()
    @Provides fun provideFailedTransactionDao(db: AppDatabase): FailedTransactionDao = db.failedTransactionDao()
}
