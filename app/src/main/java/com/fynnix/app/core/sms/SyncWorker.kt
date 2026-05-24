package com.fynnix.app.core.sms

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.fynnix.app.core.analytics.TransferPairDetector
import com.fynnix.app.core.data.UserPreferences
import com.fynnix.app.features.categories.data.DatabaseSeeder
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val reader: SmsReader,
    private val ingestion: TransactionIngestionUseCase,
    private val transferPairDetector: TransferPairDetector,
    private val seeder: DatabaseSeeder,
    private val userPrefs: UserPreferences,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            seeder.seedIfEmpty()

            val mode = inputData.getString(KEY_MODE) ?: MODE_PERIODIC

            if (mode != MODE_INITIAL && !userPrefs.isOnboardingCompleted()) {
                Log.d(TAG, "Skip $mode — onboarding not complete")
                return Result.success()
            }

            when (mode) {
                MODE_INITIAL, MODE_MANUAL -> runBulkSync(reportProgress = true,  fromScratch = true)
                MODE_PERIODIC             -> runBulkSync(reportProgress = false, fromScratch = false)
                MODE_INCREMENTAL_SMS      -> handleIncrementalSms()
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            if (inputData.getString(KEY_MODE) in setOf(MODE_INITIAL, MODE_MANUAL)) {
                SmsProcessingManager.setStatus("Failed. Retrying...")
            }
            Result.retry()
        }
    }

    private suspend fun handleIncrementalSms() {
        val sender    = inputData.getString(KEY_SENDER)                          ?: return
        val body      = inputData.getString(KEY_BODY)                            ?: return
        val timestamp = inputData.getLong(KEY_TIMESTAMP, System.currentTimeMillis())
        val id        = "INC_${timestamp}_${sender.hashCode()}"

        ingestion.ingest(RawSms(id = id, sender = sender, body = body, timestamp = timestamp), allowLlmFallback = true)

        runCatching { transferPairDetector.detectAndMark() }
            .onFailure { Log.w(TAG, "Transfer-pair detection failed (incremental)", it) }
    }

    private suspend fun runBulkSync(reportProgress: Boolean, fromScratch: Boolean) {
        val now         = System.currentTimeMillis()
        val days        = inputData.getInt(KEY_DAYS, userPrefs.getSyncDays())
        val windowStart = if (days == Int.MAX_VALUE) 0L else now - (days * 24L * 60 * 60 * 1000)
        val start       = if (fromScratch) windowStart else maxOf(windowStart, userPrefs.getLastSyncTimestamp())

        if (reportProgress) {
            SmsProcessingManager.startProcessing()
            SmsProcessingManager.setStatus("Reading messages...")
        }

        var scanned = 0
        reader.readSmsInRange(start, now).collect { raw ->
            if (reportProgress && scanned == 0) SmsProcessingManager.setStatus("Parsing transactions...")

            val result = ingestion.ingest(raw)
            scanned++

            if (reportProgress) {
                SmsProcessingManager.incScanned()
                when {
                    result.saved      > 0 -> { SmsProcessingManager.incTransactional(); SmsProcessingManager.incParsed(); SmsProcessingManager.incSaved() }
                    result.duplicates > 0 -> SmsProcessingManager.incDuplicate()
                    result.failed     > 0 -> SmsProcessingManager.incParsed()
                }
                if (scanned % BATCH_HINT == 0) SmsProcessingManager.setTotalToScan(scanned)
            }
        }

        runCatching { transferPairDetector.detectAndMark() }
            .onFailure { Log.w(TAG, "Transfer-pair detection failed", it) }

        if (reportProgress) {
            SmsProcessingManager.setTotalToScan(scanned)
            SmsProcessingManager.completeProcessing()
        }
        userPrefs.setLastSyncTimestamp(now)
        Log.d(TAG, "Bulk sync done: scanned=$scanned (mode=${inputData.getString(KEY_MODE)})")
    }

    companion object {
        private const val TAG        = "SyncWorker"
        private const val BATCH_HINT = 25

        const val KEY_MODE              = "MODE"
        const val MODE_INITIAL          = "INITIAL"
        const val MODE_MANUAL           = "MANUAL"
        const val MODE_PERIODIC         = "PERIODIC"
        const val MODE_INCREMENTAL_SMS  = "INCREMENTAL_SMS"
        const val KEY_DAYS              = "DAYS"
        const val KEY_SENDER            = "sender"
        const val KEY_BODY              = "body"
        const val KEY_TIMESTAMP         = "timestamp"
        const val UNIQUE_BULK           = "fynnix_bulk_sync"
        const val UNIQUE_PERIODIC       = "fynnix_periodic_sync"
        const val UNIQUE_INCREMENTAL    = "fynnix_incremental_sms"

        fun scheduleInitialSync(context: Context, days: Int = 90) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_BULK, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .setInputData(workDataOf(KEY_DAYS to days, KEY_MODE to MODE_INITIAL))
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()
            )
        }

        fun scheduleManualSync(context: Context, days: Int = 90) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_BULK, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .setInputData(workDataOf(KEY_DAYS to days, KEY_MODE to MODE_MANUAL))
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()
            )
        }

        fun schedulePeriodicSync(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                    .setInputData(workDataOf(KEY_MODE to MODE_PERIODIC))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()
            )
        }
    }
}
