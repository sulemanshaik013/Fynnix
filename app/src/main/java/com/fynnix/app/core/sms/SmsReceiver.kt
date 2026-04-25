package com.fynnix.app.core.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.fynnix.app.core.data.UserPreferences
import com.fynnix.app.core.utils.AppConstants
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject lateinit var userPrefs: UserPreferences

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!userPrefs.isOnboardingCompleted()) return

        if (!RateLimiter.allow(context)) {
            Log.w(TAG, "SMS rate limit exceeded — dropping incremental enqueue")
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        for (msg in messages) {
            val sender = msg.originatingAddress ?: continue
            val body   = msg.messageBody       ?: continue
            val ts     = msg.timestampMillis

            val tag = "sms_inc_${ts}_${sender.hashCode()}"
            WorkManager.getInstance(context).enqueueUniqueWork(
                tag,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequest.Builder(SyncWorker::class.java)
                    .setInputData(
                        workDataOf(
                            SyncWorker.KEY_MODE      to SyncWorker.MODE_INCREMENTAL_SMS,
                            SyncWorker.KEY_SENDER    to sender,
                            SyncWorker.KEY_BODY      to body,
                            SyncWorker.KEY_TIMESTAMP to ts,
                        )
                    )
                    .addTag(tag)
                    .build()
            )
        }
    }

    /**
     * Token-bucket rate limiter backed by SharedPreferences.
     * Allows at most [AppConstants.SMS_RATE_LIMIT_PER_MINUTE] enqueues per 60-second window.
     * No synchronisation needed — BroadcastReceiver.onReceive() is called on the main thread.
     */
    private object RateLimiter {
        private const val PREFS  = "sms_rate_limiter"
        private const val KEY_TS = "window_start"
        private const val KEY_CT = "count"
        private const val WINDOW = 60_000L  // 1 minute

        fun allow(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val now   = System.currentTimeMillis()
            val windowStart = prefs.getLong(KEY_TS, 0L)
            val count       = prefs.getInt(KEY_CT, 0)

            return if (now - windowStart > WINDOW) {
                // New window — reset
                prefs.edit().putLong(KEY_TS, now).putInt(KEY_CT, 1).apply()
                true
            } else if (count < AppConstants.SMS_RATE_LIMIT_PER_MINUTE) {
                prefs.edit().putInt(KEY_CT, count + 1).apply()
                true
            } else {
                false
            }
        }
    }

    private companion object {
        const val TAG = "SmsReceiver"
    }
}
