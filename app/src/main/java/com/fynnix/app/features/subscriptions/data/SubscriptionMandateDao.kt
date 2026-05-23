package com.fynnix.app.features.subscriptions.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fynnix.app.core.utils.MandateStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionMandateDao {

    @Query("SELECT * FROM subscription_mandates WHERE status = 'ACTIVE' ORDER BY updatedAt DESC")
    fun findActiveFlow(): Flow<List<SubscriptionMandateEntity>>

    @Query("SELECT * FROM subscription_mandates WHERE merchant = :merchant LIMIT 1")
    suspend fun findByMerchant(merchant: String): SubscriptionMandateEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(mandate: SubscriptionMandateEntity): Long

    @Query(
        "UPDATE subscription_mandates SET amount = :amount, nextDueDate = :nextDueDate, " +
            "umn = :umn, status = 'ACTIVE', updatedAt = :updatedAt WHERE id = :id"
    )
    suspend fun updateActive(id: Long, amount: Double?, nextDueDate: Long?, umn: String, updatedAt: Long)

    @Query("UPDATE subscription_mandates SET status = 'CANCELLED', updatedAt = :updatedAt WHERE merchant = :merchant")
    suspend fun cancelByMerchant(merchant: String, updatedAt: Long)

    @Query(
        "SELECT * FROM subscription_mandates WHERE status = 'ACTIVE' AND nextDueDate IS NOT NULL " +
            "AND nextDueDate BETWEEN :start AND :end " +
            "AND (notifiedForDate IS NULL OR notifiedForDate != nextDueDate)"
    )
    suspend fun findDueForNotification(start: Long, end: Long): List<SubscriptionMandateEntity>

    @Query("UPDATE subscription_mandates SET notifiedForDate = :dueDate WHERE id = :id")
    suspend fun markNotified(id: Long, dueDate: Long)
}
