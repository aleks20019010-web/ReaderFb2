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
 * WorkManager для фоновой авто-синхронизации с Яндекс Диском.
 */
class AutoSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private fun createNotificationChannel(context: Context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                val name = "Авто-синхронизация"
                val descriptionText = "Уведомления о процессе авто-синхронизации с Яндекс Диском"
                val importance = android.app.NotificationManager.IMPORTANCE_LOW
                val channel = android.app.NotificationChannel("yandex_auto_sync_channel", name, importance).apply {
                    description = descriptionText
                }
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                notificationManager.createNotificationChannel(channel)
            } catch (e: Exception) {
                Log.e("AUTO_SYNC_WORKER", "Failed to create notification channel", e)
            }
        }
    }

    override suspend fun doWork(): Result {
        Log.d("AUTO_SYNC_WORKER", "AutoSyncWorker: Начало выполнения фоновой авто-синхронизации")
        val context = applicationContext

        // Проверяем, идет ли уже какая-то синхронизация
        if (com.nightread.app.data.SyncSettingsManager.isSyncing(context)) {
            Log.d("AUTO_SYNC_WORKER", "AutoSyncWorker: Синхронизация уже выполняется, пропускаем")
            return Result.success()
        }

        // Предварительная проверка сети
        val networkChecker = SyncNetworkChecker(context)
        if (!networkChecker.isConnected()) {
            Log.e("AUTO_SYNC_WORKER", "AutoSyncWorker: Нет интернета, авто-синхронизация прервана")
            return Result.failure()
        }

        // Предварительная проверка токена
        val token = com.nightread.app.data.YandexDiskManager.getToken(context)
        if (token.isNullOrBlank()) {
            Log.e("AUTO_SYNC_WORKER", "AutoSyncWorker: Токен отсутствует, авторизуйтесь в приложении")
            return Result.failure()
        }

        val fileManager = SyncFileManager(context)
        val stateRepo = SyncStateRepository(context)

        try {
            stateRepo.updateState(true, "STARTED", 0)
            createNotificationChannel(context)

            try {
                setForeground(getForegroundInfo())
            } catch (e: Throwable) {
                Log.e("AUTO_SYNC_WORKER", "Не удалось запустить foreground режим для AutoSyncWorker", e)
            }

            // Проверить разрешения
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val hasManageStorage = android.os.Environment.isExternalStorageManager()
                val hasSaf = com.nightread.app.data.SyncSettingsManager.getDownloadFolderUri(context) != null
                if (!hasManageStorage && !hasSaf) {
                    Log.e("AUTO_SYNC_WORKER", "Missing storage access permissions")
                    stateRepo.updateState(false, "ERROR", 0, "Missing permissions")
                    return Result.failure()
                }
            } else {
                val hasReadPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (!hasReadPermission) {
                    Log.e("AUTO_SYNC_WORKER", "Missing READ_EXTERNAL_STORAGE permission")
                    stateRepo.updateState(false, "ERROR", 0, "Missing permissions")
                    return Result.failure()
                }
            }

            return withContext(Dispatchers.IO) {
                try {
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

                    val job = coroutineContext[Job]
                    job?.invokeOnCompletion {
                        if (job.isCancelled) {
                            orch.isCancelled = true
                            SyncCancellationManager.setCancelled(true)
                            Log.d("AUTO_SYNC_WORKER", "AutoSyncWorker coroutine job was cancelled.")
                        }
                    }

                    orch.sync()
                    stateRepo.updateState(false, "COMPLETED", 100)
                    Result.success()
                } catch (e: CancellationException) {
                    Log.d("AUTO_SYNC_WORKER", "AutoSyncWorker cancelled", e)
                    stateRepo.updateState(false, "CANCELLED", 0)
                    Result.failure()
                } catch (e: Throwable) {
                    SyncErrorHandler.logError("AutoSyncWorker", e, false)
                    stateRepo.updateState(false, "ERROR", 0, SyncErrorHandler.getUserFriendlyMessage(e))
                    Result.failure()
                } finally {
                    com.nightread.app.data.SyncSettingsManager.setSyncing(context, false)
                    SyncCancellationManager.reset()
                    fileManager.cleanup()
                }
            }
        } catch (e: Throwable) {
            SyncErrorHandler.logError("AutoSyncWorker Fatal", e, false)
            try {
                com.nightread.app.data.SyncSettingsManager.setSyncing(context, false)
            } catch (ex: Throwable) {
                Log.e("AUTO_SYNC_WORKER", "Failed to reset syncing flag on fatal error", ex)
            }
            return Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val context = applicationContext
        createNotificationChannel(context)

        val notification = NotificationCompat.Builder(context, "yandex_auto_sync_channel")
            .setContentTitle("Авто-синхронизация с Яндекс Диском")
            .setContentText("Выполняется автоматическая синхронизация книг...")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, 0, true)
            .build()

        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ForegroundInfo(2003, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(2003, notification)
        }
    }
}
