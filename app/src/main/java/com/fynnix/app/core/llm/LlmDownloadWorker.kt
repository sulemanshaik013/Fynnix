package com.fynnix.app.core.llm

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads [DEFAULT_LLM_MODEL] into app-private storage. Resumable via HTTP Range so a
 * killed/retried worker doesn't restart a multi-GB download from zero, and only marks the
 * model [LlmModelManager.markReady] once the downloaded byte count matches the server's
 * reported content length — a size mismatch (interrupted connection, disk full) must never
 * be mistaken for a complete model, since a truncated `.task` file fails to load silently
 * wrong rather than cleanly erroring.
 */
@HiltWorker
class LlmDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val modelManager: LlmModelManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val spec = modelManager.spec()
        val destination = modelManager.modelFile()
        val partial = java.io.File(destination.parentFile, "${destination.name}.part")

        try {
            val startByte = if (partial.exists()) partial.length() else 0L
            val connection = (URL(spec.downloadUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                if (startByte > 0) setRequestProperty("Range", "bytes=$startByte-")
            }

            val resuming = startByte > 0 && connection.responseCode == HttpURLConnection.HTTP_PARTIAL
            if (startByte > 0 && !resuming) {
                // Server doesn't support Range (or the partial file is stale) — restart clean
                // rather than appending a fresh full response onto bytes already on disk.
                partial.delete()
            }
            if (connection.responseCode !in 200..299) {
                return@withContext Result.retry()
            }

            // contentLengthLong on a 206 Partial Content response is only the remaining byte
            // count, not the full file — the resumed total has to add back what's already on
            // disk. A missing/unreliable Content-Length (-1) falls back to the spec's
            // approximate size purely to drive progress percentage; it is never treated as
            // authoritative for the completeness check below.
            val serverLength = connection.contentLengthLong
            val effectiveStartByte = if (resuming) startByte else 0L
            val expectedTotal: Long? = if (serverLength > 0) serverLength + effectiveStartByte else null
            val totalForProgress = expectedTotal ?: spec.approxSizeBytes

            RandomAccessFile(partial, "rw").use { out ->
                if (resuming) out.seek(startByte) else out.setLength(0)
                connection.inputStream.use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var written = effectiveStartByte
                    var lastReportedPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        written += read
                        val percent = ((written * 100) / totalForProgress).toInt().coerceIn(0, 100)
                        if (percent != lastReportedPercent) {
                            setProgress(workDataOf(KEY_PROGRESS_PERCENT to percent))
                            lastReportedPercent = percent
                        }
                    }
                }
            }

            val finalSize = partial.length()
            if (expectedTotal != null && finalSize != expectedTotal) {
                Log.w(TAG, "Downloaded size $finalSize != expected $expectedTotal — retrying")
                return@withContext Result.retry()
            }

            partial.renameTo(destination)
            modelManager.markReady()
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Model download failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "LlmDownloadWorker"
        private const val UNIQUE_WORK = "fynnix_llm_model_download"
        const val KEY_PROGRESS_PERCENT = "progress_percent"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val BUFFER_SIZE = 64 * 1024

        /** [requireUnmetered] defaults to true (Wi-Fi-preferred, per the download's opt-in
         *  scope) — the download screen can pass false only after the user explicitly
         *  confirms downloading over mobile data. */
        fun enqueue(context: Context, requireUnmetered: Boolean = true) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (requireUnmetered) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<LlmDownloadWorker>()
                    .setConstraints(constraints)
                    .build()
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
        }

        fun progressFlow(context: Context) =
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(UNIQUE_WORK)
    }
}
