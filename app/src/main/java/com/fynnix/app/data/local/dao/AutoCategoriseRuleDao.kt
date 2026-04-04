package com.fynnix.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fynnix.app.data.local.entity.AutoCategoriseRule
import kotlinx.coroutines.flow.Flow

@Dao
interface AutoCategoriseRuleDao {
    @Query("SELECT * FROM auto_categorise_rules ORDER BY priority ASC")
    fun getAllRules(): Flow<List<AutoCategoriseRule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: AutoCategoriseRule): Long

    @Update
    suspend fun updateRule(rule: AutoCategoriseRule)

    @Delete
    suspend fun deleteRule(rule: AutoCategoriseRule)
}