package com.fynnix.app.features.categories.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface AutoCategoriseRuleDao {
    @Query("SELECT * FROM auto_categorise_rules ORDER BY priority ASC")
    suspend fun getAllList(): List<AutoCategoriseRule>

    @Query("SELECT COUNT(*) FROM auto_categorise_rules")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: AutoCategoriseRule): Long

    @Update
    suspend fun updateRule(rule: AutoCategoriseRule)

    @Delete
    suspend fun deleteRule(rule: AutoCategoriseRule)
}