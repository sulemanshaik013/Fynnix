package com.fynnix.app.features.subscriptions.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fynnix.app.core.utils.MandateStatus

/**
 * A recurring debit confirmed directly from a bank's e-mandate / standing-instruction
 * SMS, rather than inferred statistically from transaction history. One row per
 * merchant — a later mandate SMS for the same merchant (e.g. a plan-amount change)
 * updates the existing row instead of creating a second one.
 */
@Entity(
    tableName = "subscription_mandates",
    indices = [Index(value = ["merchant"], unique = true)],
)
data class SubscriptionMandateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val merchant: String,
    val amount: Double?,
    val umn: String,
    val nextDueDate: Long?,
    val status: MandateStatus,
    /** The [nextDueDate] value a bill-due notification was last sent for — distinct from
     *  the date itself so a rollover to the next billing cycle (a new [nextDueDate]) is
     *  recognized as needing a fresh notification instead of being treated as already sent. */
    val notifiedForDate: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)
