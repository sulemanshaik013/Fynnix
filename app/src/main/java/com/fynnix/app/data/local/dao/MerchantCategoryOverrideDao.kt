package com.fynnix.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fynnix.app.data.local.entity.MerchantCategoryOverride
import kotlinx.coroutines.flow.Flow

@Dao
interface MerchantCategoryOverrideDao {
    @Query("SELECT * FROM merchant_category_overrides")
    fun getAllOverrides(): Flow<List<MerchantCategoryOverride>>

    @Query("SELECT * FROM merchant_category_overrides WHERE merchantKey = :merchantKey LIMIT 1")
    suspend fun getOverrideByMerchantKey(merchantKey: String): MerchantCategoryOverride?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOverride(override: MerchantCategoryOverride): Long

    @Update
    suspend fun updateOverride(override: MerchantCategoryOverride)

    @Delete
    suspend fun deleteOverride(override: MerchantCategoryOverride)
}