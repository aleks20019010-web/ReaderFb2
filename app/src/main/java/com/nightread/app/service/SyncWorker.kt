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
        
        val isAuto = inputData.getBoolean("is_auto", false)
        if (isAuto) {
            // 1. Проверяем, идет ли уже ручная синхронизация
            val workManager = androidx.work.WorkManager.getInstance(context)
            val manualWorkInfos = try {
                workManager.getWorkInfosForUniqueWork("YandexSyncUniqueWork").get()
            } catch (e: Exception) {
                emptyList()
            }
            val isManualRunning = manualWorkInfos.any { it.state == androidx.work.WorkInfo.State.RUNNING }
            if (isManualRunning || com.nightread.app.data.SyncSettingsManager.isSyncing(context)) {
                Log.d("SYNC_WORKER", "SyncWorker: Ручная синхронизация уже выполняется, пропускаем авто-синхронизацию")
                return Result.success()
            }

            // 2. Проверяем наличие книг в базе данных
            val db = com.nightread.app.data.AppDatabase.getDatabase(context)
            val bookDao = db.bookDao()
            val hasBooks = try {
                bookDao.getAllSha1s().isNotEmpty()
            } catch (e: Exception) {
                false
            }
            if (!hasBooks) {
                Log.d("SYNC_WORKER", "SyncWorker: В базе данных нет книг, пропускаем авто-синхронизацию")
                return Result.success()
            }
        }

        // Предварительная проверка токена
        val token = com.nightread.app.data.YandexDiskManager.getToken(context)
        if (token.isNullOrBlank()) {
            Log.e("SYNC_WORKER", "SyncWorker: Токен отсутствует, авторизуйтесь в приложении")
            YandexSyncState.update {
                it.copy(
                    isRunning = false,
                    stage = YandexSyncState.Stage.ERROR,
                    statusText = "Ошибка: Авторизуйтесь на Яндекс Диске",
                    finished = true,
                    success = false,
                    error = "Ошибка: Авторизуйтесь на Яндекс Диске"
                )
            }
            return Result.failure()
        }

        // Предварительная проверка сети
        val networkChecker = SyncNetworkChecker(context)
        if (!networkChecker.isConnected()) {
            Log.e("SYNC_WORKER", "SyncWorker: Нет интернета, синхронизация прервана")
            YandexSyncState.update {
                it.copy(
                    isRunning = false,
                    stage = YandexSyncState.Stage.ERROR,
                    statusText = "Отсутствует подключение к интернету",
                    finished = true,
                    success = false,
                    error = "Отсутствует подключение к интернету"
                )
            }
            return Result.failure()
        }

        val fileManager = SyncFileManager(context)
        val stateRepo = SyncStateRepository(context)

        try {
            stateRepo.updateState(true, "STARTED", 0)
            YandexSyncState.update {
                it.copy(
                    isRunning = true,
                    stage = YandexSyncState.Stage.PREPARING,
                    statusText = "Подготовка к синхронизации...",
                    finished = false,
                    error = null
                )
            }
            
            // Убедиться, что канал уведомлений создаётся до показа уведомления
            createNotificationChannel(context)

            try {
                setForeground(getForegroundInfo())
            } catch (e: Throwable) {
                Log.w("SYNC_WORKER", "Не удалось запустить foreground режим для WorkManager (продолжаем в фоне)", e)
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
                            Log.d("SYNC_WORKER", "SyncWorker coroutine job was cancelled, cancelling orchestrator.")
                        }
                    }

                    orch.sync()
                    stateRepo.updateState(false, "COMPLETED", 100)
                    Result.success()
                } catch (e: CancellationException) {
                    Log.d("SYNC_WORKER", "SyncWorker cancelled", e)
                    stateRepo.updateState(false, "CANCELLED", 0)
                    YandexSyncState.update {
                        it.copy(
                            isRunning = false,
                            stage = YandexSyncState.Stage.IDLE,
                            statusText = "Синхронизация отменена",
                            finished = true,
                            success = false,
                            error = "Синхронизация отменена"
                        )
                    }
                    Result.failure()
                } catch (e: Throwable) {
                    SyncErrorHandler.logError("SyncWorker", e, false)
                    val errMsg = SyncErrorHandler.getUserFriendlyMessage(e)
                    stateRepo.updateState(false, "ERROR", 0, errMsg)
                    YandexSyncState.update {
                        it.copy(
                            isRunning = false,
                            stage = YandexSyncState.Stage.ERROR,
                            statusText = errMsg,
                            finished = true,
                            success = false,
                            error = errMsg
                        )
                    }
                    Result.failure()
                } finally {
                    com.nightread.app.data.SyncSettingsManager.setSyncing(context, false)
                    SyncCancellationManager.reset()
                    fileManager.cleanup()
                }
            }
        } catch (e: Throwable) {
            SyncErrorHandler.logError("SyncWorker Fatal", e, false)
            val errMsg = e.localizedMessage ?: "Критическая ошибка синхронизации"
            YandexSyncState.update {
                it.copy(
                    isRunning = false,
                    stage = YandexSyncState.Stage.ERROR,
                    statusText = errMsg,
                    finished = true,
                    success = false,
                    error = errMsg
                )
            }
            try {
                com.nightread.app.data.SyncSettingsManager.setSyncing(context, false)
            } catch (ex: Throwable) {
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
            
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ForegroundInfo(2002, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(2002, notification)
        }
    }
}
