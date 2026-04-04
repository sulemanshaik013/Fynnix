package com.fynnix.app.di

import android.content.Context
import androidx.room.Room
import com.fynnix.app.data.local.AppDatabase
import com.fynnix.app.data.local.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "fynnix_db"
        ).build()
    }

    @Provides
    fun provideAccountDao(appDatabase: AppDatabase): AccountDao = appDatabase.accountDao()

    @Provides
    fun provideCategoryDao(appDatabase: AppDatabase): CategoryDao = appDatabase.categoryDao()

    @Provides
    fun provideTransactionDao(appDatabase: AppDatabase): TransactionDao = appDatabase.transactionDao()

    @Provides
    fun provideMerchantCategoryOverrideDao(appDatabase: AppDatabase): MerchantCategoryOverrideDao = 
        appDatabase.merchantCategoryOverrideDao()

    @Provides
    fun provideAutoCategoriseRuleDao(appDatabase: AppDatabase): AutoCategoriseRuleDao = 
        appDatabase.autoCategoriseRuleDao()

    @Provides
    fun provideBudgetDao(appDatabase: AppDatabase): BudgetDao = appDatabase.budgetDao()

    @Provides
    fun provideUserConfigDao(appDatabase: AppDatabase): UserConfigDao = appDatabase.userConfigDao()
}