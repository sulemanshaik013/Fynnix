package com.fynnix.app.features.categories.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface MerchantCategoryOverrideDao {
    @Query("SELECT * FROM merchant_category_overrides WHERE merchantKey = :merchantKey LIMIT 1")
    suspend fun getOverrideByMerchantKey(merchantKey: String): MerchantCategoryOverride?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOverride(override: MerchantCategoryOverride): Long

    @Update
    suspend fun updateOverride(override: MerchantCategoryOverride)

    @Delete
    suspend fun deleteOverride(override: MerchantCategoryOverride)
}