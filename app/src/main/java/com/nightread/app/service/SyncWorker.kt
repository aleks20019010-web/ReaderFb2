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

    private fun createNotificationChannel(context: Context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                val name = "Синхронизация"
                val descriptionText = "Уведомления о процессе синхронизации с Яндекс Диском"
                val importance = android.app.NotificationManager.IMPORTANCE_LOW
                val channel = android.app.NotificationChannel("yandex_sync_channel", name, importance).apply {
                    description = descriptionText
                }
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                notificationManager.createNotificationChannel(channel)
                Log.d("SYNC_WORKER", "Notification channel 'yandex_sync_channel' successfully created/verified.")
            } catch (e: Exception) {
                Log.e("SYNC_WORKER", "Failed to create notification channel", e)
            }
        }
    }

    override suspend fun doWork(): Result {
        Log.d("SYNC_WORKER", "SyncWorker: Начало выполнения фоновой синхронизации")
        val context = applicationContext
        val networkChecker: SyncNetworkChecker
        val fileManager: SyncFileManager
        val stateRepo: SyncStateRepository

        try {
            networkChecker = SyncNetworkChecker(context)
            fileManager = SyncFileManager(context)
            stateRepo = SyncStateRepository(context)
        } catch (e: Throwable) {
            Log.e("SYNC_WORKER", "SyncWorker: Crash in initialization", e)
            return Result.failure()
        }

        if (!networkChecker.isConnected()) {
            Log.e("SYNC_WORKER", "SyncWorker: Нет интернета")
            return Result.failure()
        }

        try {
            stateRepo.updateState(true, "STARTED", 0)
            // 2. Убедиться, что канал уведомлений создаётся до показа уведомления
            createNotificationChannel(context)

            try {
                setForeground(getForegroundInfo())
            } catch (e: Exception) {
                Log.e("SYNC_WORKER", "Не удалось запустить foreground режим для WorkManager", e)
            }

            // 5. Проверить разрешения
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val hasManageStorage = android.os.Environment.isExternalStorageManager()
                val hasSaf = com.nightread.app.data.SyncSettingsManager.getDownloadFolderUri(context) != null
                if (!hasManageStorage && !hasSaf) {
                    Log.e("SYNC_WORKER", "Missing storage access permissions on Android 11+: MANAGE_EXTERNAL_STORAGE is not granted and SAF folder is not set.")
                    stateRepo.updateState(false, "ERROR", 0, "Missing permissions")
                    return Result.failure()
                }
            } else {
                val hasReadPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (!hasReadPermission) {
                    Log.e("SYNC_WORKER", "Missing READ_EXTERNAL_STORAGE permission on Android 10 or below.")
                    stateRepo.updateState(false, "ERROR", 0, "Missing permissions")
                    return Result.failure()
                }
            }

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
                            SyncCancellationManager.setCancelled(true)
                            Log.d("SYNC_WORKER", "SyncWorker coroutine job was cancelled, cancelling orchestrator.")
                        }
                    }

                    orch.sync()
                    stateRepo.updateState(false, "COMPLETED", 100)
                    Result.success()
                } catch (e: CancellationException) {
                    Log.d("SYNC_WORKER", "SyncWorker cancelled", e)
                    stateRepo.updateState(false, "CANCELLED", 0)
                    Result.failure()
                } catch (e: Exception) {
                    SyncErrorHandler.logError("SyncWorker", e, false)
                    stateRepo.updateState(false, "ERROR", 0, SyncErrorHandler.getUserFriendlyMessage(e))
                    Result.failure()
                } finally {
                    // Reset isSyncing flag to false
                    com.nightread.app.data.SyncSettingsManager.setSyncing(context, false)
                    SyncCancellationManager.reset()
                    fileManager.cleanup()
                }
            }
        } catch (e: Exception) {
            SyncErrorHandler.logError("SyncWorker Fatal", e, false)
            // Ensure flag is reset in case of fatal error
            try {
                com.nightread.app.data.SyncSettingsManager.setSyncing(context, false)
            } catch (ex: Exception) {
                Log.e("SYNC_WORKER", "Failed to reset syncing flag on fatal error", ex)
            }
            return Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val context = applicationContext
        createNotificationChannel(context)

        val notification = NotificationCompat.Builder(context, "yandex_sync_channel")
            .setContentTitle("Синхронизация с Яндекс Диском")
            .setContentText("Запуск фоновой синхронизации...")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, 0, true)
            .build()
        return ForegroundInfo(2002, notification)
    }
}
