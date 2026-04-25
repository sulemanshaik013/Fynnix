package com.fynnix.app.core.sms

import android.content.Context
import android.provider.Telephony
import android.util.Log
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class SmsReader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Streams SMS messages in [start, end] one at a time using a cursor so
     * inbox size has no impact on heap usage.
     *
     * Emits nothing (empty flow) if READ_SMS has been revoked at query time;
     * callers treat the empty stream as "no messages" without crashing.
     */
    fun readSmsInRange(start: Long, end: Long): Flow<RawSms> = flow {
        val cursor = try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms._ID
                ),
                "${Telephony.Sms.DATE} BETWEEN ? AND ?",
                arrayOf(start.toString(), end.toString()),
                "${Telephony.Sms.DATE} DESC"
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "READ_SMS permission revoked — cannot query inbox", e)
            return@flow
        }

        cursor?.use { c ->
            val colAddress = c.getColumnIndex(Telephony.Sms.ADDRESS)
            val colBody    = c.getColumnIndex(Telephony.Sms.BODY)
            val colDate    = c.getColumnIndex(Telephony.Sms.DATE)
            val colId      = c.getColumnIndex(Telephony.Sms._ID)

            while (c.moveToNext()) {
                emit(
                    RawSms(
                        id        = c.getStringOrNull(colId)      ?: continue,
                        sender    = c.getStringOrNull(colAddress) ?: continue,
                        body      = c.getStringOrNull(colBody)    ?: continue,
                        timestamp = c.getLongOrNull(colDate)      ?: continue,
                    )
                )
            }
        }
    }

    private companion object {
        const val TAG = "SmsReader"
    }
}

data class RawSms(
    val id: String,
    val sender: String,
    val body: String,
    val timestamp: Long,
)
