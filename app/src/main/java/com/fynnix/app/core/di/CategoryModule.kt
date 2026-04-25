package com.fynnix.app.core.di

import com.fynnix.app.features.categories.domain.CategoryClassifier
import com.fynnix.app.features.categories.domain.ChainedCategoryClassifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CategoryModule {

    @Binds
    @Singleton
    abstract fun bindCategoryClassifier(impl: ChainedCategoryClassifier): CategoryClassifier
}
