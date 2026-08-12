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
            val scanner = com.nightread.app.scanner.LibraryScanner(context, bookDao)
            
            val initialCount = try { bookDao.getSha1ToPathMap().size } catch (e: Throwable) { 0 }
            scanner.scanBooks().join()
            val newCount = try { bookDao.getSha1ToPathMap().size } catch (e: Throwable) { initialCount }
            
            val added = newCount - initialCount
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    val msg = if (added > 0) {
                        "Найдено новых книг: $added"
                    } else {
                        "Новых книг не найдено"
                    }
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                } catch (e: Throwable) {}
            }
            
            Log.d("AutoDiscoveryWorker", "Finished auto-discovery scan")
            return Result.success()
        } catch (e: Throwable) {
            Log.e("AutoDiscoveryWorker", "Error in auto-discovery scan", e)
            return Result.failure()
        }
    }

    private fun showNewBooksNotification(addedCount: Int) {
        // Disabled as requested
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
