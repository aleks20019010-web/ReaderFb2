package com.nightread.app.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nightread.app.data.AppDatabase
import java.util.concurrent.TimeUnit

class AutoDiscoveryWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("AutoDiscoveryWorker", "Starting auto-discovery scan")
        try {
            val bookDao = AppDatabase.getDatabase(context).bookDao()
            val scanner = NewBookScanner(context, bookDao)
            
            val initialCount = bookDao.getSha1ToPathMap().size
            scanner.scanBooks(isBackground = true)
            val newCount = bookDao.getSha1ToPathMap().size
            
            val added = newCount - initialCount
            if (added > 0) {
                showNewBooksNotification(added)
            }
            
            Log.d("AutoDiscoveryWorker", "Finished auto-discovery scan")
            return Result.success()
        } catch (e: Exception) {
            Log.e("AutoDiscoveryWorker", "Error in auto-discovery scan", e)
            return Result.failure()
        }
    }

    private fun showNewBooksNotification(addedCount: Int) {
        // Disabled as requested - show no notifications except TTS
    }

    companion object {
        private const val WORK_NAME = "AutoDiscoveryWorker"

        fun schedule(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<AutoDiscoveryWorker>(
                6, TimeUnit.HOURS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }

        fun runOnce(context: Context) {
            val workRequest = androidx.work.OneTimeWorkRequestBuilder<AutoDiscoveryWorker>()
                .addTag("AutoDiscoveryOnce")
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "AutoDiscoveryOnce",
                androidx.work.ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
