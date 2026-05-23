package com.fynnix.app.features.reminders.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A due-date bill/statement reminder parsed from SMS (credit card statements, loan EMIs,
 * utility bills) — distinct from [com.fynnix.app.features.subscriptions.data.SubscriptionMandateEntity],
 * which tracks a confirmed *recurring debit*. A reminder is a one-off due date each time it's
 * re-parsed; there's no mandate/UMN backing it. One row per label — a later reminder SMS for
 * the same label (next month's statement) updates the existing row rather than creating a new one.
 */
@Entity(
    tableName = "bill_reminders",
    indices = [Index(value = ["label"], unique = true)],
)
data class BillReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val label: String,
    val amount: Double?,
    val dueDate: Long?,
    /** The [dueDate] value a notification was last sent for — lets a rolled-over due date
     *  (next month's bill) be recognized as needing a fresh notification. */
    val notifiedForDate: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)
