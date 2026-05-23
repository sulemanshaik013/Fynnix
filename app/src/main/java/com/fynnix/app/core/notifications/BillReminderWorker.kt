package com.fynnix.app.core.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fynnix.app.core.utils.AppConstants
import com.fynnix.app.features.reminders.data.BillReminderDao
import com.fynnix.app.features.subscriptions.data.SubscriptionMandateDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Daily check for confirmed subscriptions and bill reminders due within
 * [AppConstants.BILL_NOTIFY_WINDOW_DAYS] — fires one local notification per item, at most
 * once per due date (see notifiedForDate on each entity).
 */
@HiltWorker
class BillReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val mandateDao: SubscriptionMandateDao,
    private val reminderDao: BillReminderDao,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Start from the beginning of today, not the current instant — a due date is stored
        // as a day's midnight timestamp, so anchoring the window to "now" would miss a bill
        // due "today" once the worker happens to run later than midnight on that same day.
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val windowEnd = todayStart + AppConstants.BILL_NOTIFY_WINDOW_DAYS * AppConstants.DAY_MILLIS

        mandateDao.findDueForNotification(todayStart, windowEnd).forEach { mandate ->
            val dueDate = mandate.nextDueDate ?: return@forEach
            BillNotifier.notify(
                context = applicationContext,
                notificationId = "mandate-${mandate.id}".hashCode(),
                title = "${mandate.merchant} is due soon",
                text = buildText(mandate.amount, dueDate),
            )
            mandateDao.markNotified(mandate.id, dueDate)
        }

        reminderDao.findDueForNotification(todayStart, windowEnd).forEach { reminder ->
            val dueDate = reminder.dueDate ?: return@forEach
            BillNotifier.notify(
                context = applicationContext,
                notificationId = "reminder-${reminder.id}".hashCode(),
                title = "${reminder.label} due soon",
                text = buildText(reminder.amount, dueDate),
            )
            reminderDao.markNotified(reminder.id, dueDate)
        }

        return Result.success()
    }

    private fun buildText(amount: Double?, dueDate: Long): String {
        val dateLabel = DATE_FORMAT.format(Date(dueDate))
        return if (amount != null) "₹${"%,.0f".format(amount)} due $dateLabel" else "Due $dateLabel"
    }

    private companion object {
        val DATE_FORMAT = SimpleDateFormat("d MMM", Locale.getDefault())
    }

    object Scheduler {
        private const val UNIQUE_WORK = "fynnix_bill_reminders"

        fun schedulePeriodic(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<BillReminderWorker>(1, TimeUnit.DAYS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
                    .build()
            )
        }
    }
}
