package com.fynnix.app.features.transactions.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class CategorySpendRow(
    val categoryId: Long,
    val total: Double,
)

data class MerchantGroupRow(
    val merchant: String,
    val txCount: Int,
    val avgAmount: Double,
)

@Dao
interface TransactionDao {

    /** SMS-sourced inserts — preserves any prior row (and its user edits). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(transaction: TransactionEntity): Long

    /** Manual / CSV inserts where the caller wants overwrite-on-conflict. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReplace(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(transactions: List<TransactionEntity>)

    @Update
    suspend fun update(transaction: TransactionEntity)

    // ---- Single-row lookups ----

    @Query("SELECT * FROM transactions WHERE id = :id AND deletedAt IS NULL")
    suspend fun getById(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE smsHash = :hash LIMIT 1")
    suspend fun findBySmsHash(hash: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE dedupKey = :key AND deletedAt IS NULL LIMIT 1")
    suspend fun findByDedupKey(key: String): TransactionEntity?

    // ---- Streaming queries (all filter out soft-deleted rows) ----

    @Query("SELECT * FROM transactions WHERE deletedAt IS NULL ORDER BY dateTime DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE deletedAt IS NULL ORDER BY dateTime DESC LIMIT :limit")
    fun getRecent(limit: Int = 50): Flow<List<TransactionEntity>>

    @Query(
        "SELECT * FROM transactions WHERE deletedAt IS NULL " +
            "AND dateTime BETWEEN :startTime AND :endTime ORDER BY dateTime DESC"
    )
    fun getByDateRange(startTime: Long, endTime: Long): Flow<List<TransactionEntity>>

    @Query(
        "SELECT IFNULL(SUM(amount),0) FROM transactions " +
            "WHERE type = 'DEBIT' AND isExcluded = 0 AND deletedAt IS NULL " +
            "AND dateTime BETWEEN :start AND :end"
    )
    fun sumDebitsFlow(start: Long, end: Long): Flow<Double>

    @Query(
        "SELECT IFNULL(SUM(amount),0) FROM transactions " +
            "WHERE type = 'CREDIT' AND isExcluded = 0 AND deletedAt IS NULL " +
            "AND dateTime BETWEEN :start AND :end"
    )
    fun sumCreditsFlow(start: Long, end: Long): Flow<Double>

    @Query(
        "SELECT categoryId AS categoryId, SUM(amount) AS total FROM transactions " +
            "WHERE type = 'DEBIT' AND isExcluded = 0 AND deletedAt IS NULL " +
            "AND dateTime BETWEEN :start AND :end " +
            "GROUP BY categoryId ORDER BY total DESC"
    )
    fun categorySpendFlow(start: Long, end: Long): Flow<List<CategorySpendRow>>

    @Query(
        "SELECT merchant AS merchant, COUNT(*) AS txCount, AVG(amount) AS avgAmount FROM transactions " +
            "WHERE type = 'DEBIT' AND isExcluded = 0 AND deletedAt IS NULL AND merchant <> '' " +
            "GROUP BY merchant HAVING txCount >= 3"
    )
    fun recurringCandidatesFlow(): Flow<List<MerchantGroupRow>>

    @Query(
        "SELECT * FROM transactions WHERE merchant = :merchant AND type = 'DEBIT' " +
            "AND isExcluded = 0 AND deletedAt IS NULL ORDER BY dateTime ASC"
    )
    suspend fun txByMerchant(merchant: String): List<TransactionEntity>

    // ---- Mutations ----

    @Query("UPDATE transactions SET isExcluded = :excluded, isUserEdited = 1 WHERE id = :id")
    suspend fun markExcluded(id: String, excluded: Boolean)

    /** Soft-delete: sets deletedAt timestamp instead of removing the row. */
    @Query("UPDATE transactions SET deletedAt = :timestamp WHERE id = :id")
    suspend fun softDelete(id: String, timestamp: Long = System.currentTimeMillis())

    /** Restore a soft-deleted transaction. */
    @Query("UPDATE transactions SET deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: String)

    // ---- Aggregates ----

    @Query("SELECT COUNT(*) FROM transactions WHERE deletedAt IS NULL")
    suspend fun getCount(): Long

    // ---- Admin / backup (include all rows regardless of deletion state) ----

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("DELETE FROM transactions")
    suspend fun clearAll()
}
