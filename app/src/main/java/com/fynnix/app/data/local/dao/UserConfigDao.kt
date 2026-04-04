package com.fynnix.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fynnix.app.data.local.entity.UserConfig
import kotlinx.coroutines.flow.Flow

@Dao
interface UserConfigDao {
    @Query("SELECT * FROM user_config WHERE id = 1 LIMIT 1")
    fun getUserConfig(): Flow<UserConfig?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(userConfig: UserConfig)

    @Update
    suspend fun update(userConfig: UserConfig)
}