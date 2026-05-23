package com.fynnix.app.features.reminders.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BillReminderDao {

    @Query("SELECT * FROM bill_reminders WHERE dueDate IS NOT NULL ORDER BY dueDate ASC")
    fun findUpcomingFlow(): Flow<List<BillReminderEntity>>

    @Query("SELECT * FROM bill_reminders WHERE label = :label LIMIT 1")
    suspend fun findByLabel(label: String): BillReminderEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(reminder: BillReminderEntity): Long

    @Query(
        "UPDATE bill_reminders SET amount = :amount, dueDate = :dueDate, " +
            "notifiedForDate = :notifiedForDate, updatedAt = :updatedAt WHERE id = :id"
    )
    suspend fun updateReminder(id: Long, amount: Double?, dueDate: Long?, notifiedForDate: Long?, updatedAt: Long)

    @Query(
        "SELECT * FROM bill_reminders WHERE dueDate IS NOT NULL AND dueDate BETWEEN :start AND :end " +
            "AND (notifiedForDate IS NULL OR notifiedForDate != dueDate)"
    )
    suspend fun findDueForNotification(start: Long, end: Long): List<BillReminderEntity>

    @Query("UPDATE bill_reminders SET notifiedForDate = :dueDate WHERE id = :id")
    suspend fun markNotified(id: Long, dueDate: Long)
}
