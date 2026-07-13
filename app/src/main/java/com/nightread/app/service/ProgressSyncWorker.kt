package com.nightread.app.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nightread.app.data.YandexDiskManager

/**
 * Background incremental reading progress synchronization worker.
 * Initiates direct progress uploads on app lock, close, or background transition.
 */
class ProgressSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val sha1 = inputData.getString("BOOK_SHA1") ?: return Result.failure()
        val charOffset = inputData.getInt("CHAR_OFFSET", -1)
        if (charOffset == -1) return Result.failure()

        Log.d(TAG, "Starting background progress sync for book: SHA1=$sha1, offset=$charOffset")
        
        try {
            YandexDiskManager.pushProgressToCloud(applicationContext, sha1, charOffset)
            Log.d(TAG, "Background progress sync completed successfully for: SHA1=$sha1")
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading progress in background for: SHA1=$sha1", e)
            return Result.retry()
        }
    }

    companion object {
        private const val TAG = "ProgressSyncWorker"

        /**
         * Enqueues a progress synchronization task with network constraints and exponential backoff.
         */
        fun scheduleProgressSync(context: Context, sha1: String, charOffset: Int) {
            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build()

            val data = androidx.work.workDataOf(
                "BOOK_SHA1" to sha1,
                "CHAR_OFFSET" to charOffset
            )

            val workRequest = androidx.work.OneTimeWorkRequestBuilder<ProgressSyncWorker>()
                .setConstraints(constraints)
                .setInputData(data)
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    androidx.work.WorkRequest.MIN_BACKOFF_MILLIS,
                    java.util.concurrent.TimeUnit.MILLISECONDS
                )
                .build()

            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                "ProgressSync_$sha1",
                androidx.work.ExistingWorkPolicy.REPLACE,
                workRequest
            )
            Log.d(TAG, "Enqueued background progress sync for SHA1=$sha1, offset=$charOffset")
        }
    }
}
