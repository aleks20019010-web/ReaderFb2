package com.nightread.app.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.core.app.NotificationCompat
import com.nightread.app.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext

/**
 * WorkManager для фоновой синхронизации с Яндекс Диском.
 * Позволяет выполнять надежную и независимую фоновую операцию с использованием ForegroundInfo.
 */
class SyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "SyncWorker: Начало выполнения фоновой синхронизации")
        
        try {
            setForeground(getForegroundInfo())
        } catch (e: Exception) {
            Log.e("SYNC_ERROR", "Не удалось запустить foreground режим для WorkManager", e)
        }

        val context = applicationContext
        return withContext(Dispatchers.IO) {
            try {
                // Set the isSyncing flag before running synchronization
                com.nightread.app.data.SyncSettingsManager.setSyncing(context, true)

                val cloudService = CloudFileService(context)
                val sha1Extractor = Sha1Extractor()
                val db = AppDatabase.getDatabase(context)
                val cacheManager = SyncCacheManager(db.cloudFileDao())
                val progressTracker = SyncProgressTracker(context)

                val orch = SyncOrchestrator(
                    context,
                    cloudService,
                    sha1Extractor,
                    cacheManager,
                    progressTracker
                )

                // Подписываемся на отмену Coroutine Job для передачи сигнала отмены в оркестратор
                val job = coroutineContext[Job]
                job?.invokeOnCompletion {
                    if (job.isCancelled) {
                        orch.isCancelled = true
                        Log.d(TAG, "SyncWorker coroutine job was cancelled, cancelling orchestrator.")
                    }
                }

                orch.sync()
                Result.success()
            } catch (e: CancellationException) {
                Log.d(TAG, "SyncWorker cancelled", e)
                Result.failure()
            } catch (e: Exception) {
                // Log all exceptions with full stack trace and SYNC_ERROR tag
                Log.e("SYNC_ERROR", "Exception occurred during background synchronization in SyncWorker", e)
                Result.failure()
            } finally {
                // Reset isSyncing flag to false
                com.nightread.app.data.SyncSettingsManager.setSyncing(context, false)
                
                // Cleanup temporary/leftover files
                try {
                    val cacheDir = context.cacheDir
                    cacheDir.listFiles()?.forEach { file ->
                        if (file.name.startsWith("temp_sha_") || file.name.startsWith("temp_down_")) {
                            file.delete()
                        }
                    }
                } catch (cleanupEx: Exception) {
                    Log.e("SYNC_ERROR", "Failed to cleanup temporary files in SyncWorker finally block", cleanupEx)
                }
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, "yandex_sync_channel")
            .setContentTitle("Синхронизация с Яндекс Диском")
            .setContentText("Запуск фоновой синхронизации...")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, 0, true)
            .build()
        return ForegroundInfo(2002, notification)
    }

    companion object {
        private const val TAG = "SyncWorker"
    }
}
