package com.fynnix.app.features.transactions.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FailedTransactionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(failed: FailedTransactionEntity): Long

    @Query("SELECT COUNT(*) FROM failed_transactions")
    fun countFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM failed_transactions")
    suspend fun count(): Int

    @Query("SELECT * FROM failed_transactions ORDER BY lastAttempt DESC")
    fun getAllFlow(): Flow<List<FailedTransactionEntity>>

    @Query("SELECT * FROM failed_transactions WHERE retryCount < :maxRetries ORDER BY lastAttempt ASC")
    suspend fun getRetryable(maxRetries: Int = 3): List<FailedTransactionEntity>

    @Update
    suspend fun update(failed: FailedTransactionEntity)

    @Delete
    suspend fun delete(failed: FailedTransactionEntity)

    @Query("DELETE FROM failed_transactions")
    suspend fun clearAll()
}
