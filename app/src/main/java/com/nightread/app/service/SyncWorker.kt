package com.nightread.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.nightread.app.MainActivity
import com.nightread.app.data.YandexSyncManager
import com.nightread.app.data.YandexSyncState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WorkManager для фоновой синхронизации с Яндекс Диском.
 * Позволяет выполнять надежную и независимую фоновую операцию с использованием ForegroundInfo.
 */
class SyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "yandex_sync_channel"
    private val notificationId = 2002

    override suspend fun doWork(): Result {
        Log.d(TAG, "SyncWorker: Начало выполнения фоновой синхронизации")
        
        // 1. Создаем канал уведомлений и инициализируем Foreground службу WorkManager
        createNotificationChannel()
        try {
            setForeground(getForegroundInfo())
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось запустить foreground режим для WorkManager", e)
        }

        // 2. Сбрасываем и инициализируем состояние для UI-наблюдателей
        YandexSyncState.update {
            it.copy(
                isRunning = true,
                stage = YandexSyncState.Stage.PREPARING,
                statusText = "Подготовка к синхронизации...",
                completed = 0,
                total = 0,
                percent = 0,
                remainingTimeSeconds = -1L,
                downloadedCount = 0,
                uploadedCount = 0,
                finished = false,
                success = false,
                error = null
            )
        }

        val syncManager = YandexSyncManager(applicationContext)

        return withContext(Dispatchers.IO) {
            try {
                // ЭТАП 1: Анализ диска (список файлов, загрузка временных файлов, SHA-1, сравнение)
                YandexSyncState.update {
                    it.copy(
                        stage = YandexSyncState.Stage.SCANNING,
                        statusText = "Получение списка файлов с диска..."
                    )
                }
                updateNotification("Получение списка файлов с диска...", 0, 100, true)

                val stats = syncManager.calculateSyncStats { progressText ->
                    YandexSyncState.update { it.copy(statusText = progressText) }
                    updateNotification(progressText, 0, 100, true)
                }

                if (stats == null) {
                    val errorMsg = "Ошибка при анализе Яндекс Диска. Проверьте авторизацию."
                    handleError(errorMsg)
                    return@withContext Result.failure()
                }

                // ЭТАП 2: Непосредственная синхронизация (Скачивание, Загрузка, прогресс, удаление дубликатов)
                val success = syncManager.performSync(stats) { status, completed, total, stage, downloaded, uploaded, remaining ->
                    val percent = if (total > 0) (completed * 100) / total else 0

                    YandexSyncState.update {
                        it.copy(
                            stage = stage,
                            statusText = status,
                            completed = completed,
                            total = total,
                            percent = percent,
                            remainingTimeSeconds = remaining,
                            downloadedCount = downloaded,
                            uploadedCount = uploaded
                        )
                    }

                    val remainingText = if (remaining > 0) {
                        val mins = remaining / 60
                        val secs = remaining % 60
                        if (mins > 0) " (осталось ~${mins}м ${secs}с)" else " (осталось ~${secs}с)"
                    } else ""

                    updateNotification("$status$remainingText", percent, 100, false)
                }

                if (success) {
                    handleSuccess()
                    Result.success()
                } else {
                    handleError("Ошибка во время синхронизации данных.")
                    Result.failure()
                }

            } catch (e: CancellationException) {
                handleCancellation()
                Result.failure()
            } catch (e: Exception) {
                Log.e(TAG, "Исключение при выполнении SyncWorker", e)
                handleError(e.localizedMessage ?: "Неизвестная ошибка")
                Result.failure()
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Синхронизация с Яндекс Диском")
            .setContentText("Запуск фоновой синхронизации...")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, 0, true)
            .setContentIntent(getMainActivityPendingIntent())
            .build()
        return ForegroundInfo(notificationId, notification)
    }

    private fun updateNotification(text: String, progress: Int, max: Int, indeterminate: Boolean) {
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Синхронизация с Яндекс Диском")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(max, progress, indeterminate)
            .setContentIntent(getMainActivityPendingIntent())
            .build()
        notificationManager.notify(notificationId, notification)
    }

    private fun handleSuccess() {
        val finalState = YandexSyncState.state.value
        YandexSyncState.update {
            it.copy(
                isRunning = false,
                stage = YandexSyncState.Stage.COMPLETED,
                statusText = "Синхронизация успешно завершена!",
                finished = true,
                success = true,
                error = null
            )
        }
        showFinalNotification(
            "Синхронизация завершена",
            "Скачано: ${finalState.downloadedCount}, Загружено: ${finalState.uploadedCount}."
        )
    }

    private fun handleError(message: String) {
        YandexSyncState.update {
            it.copy(
                isRunning = false,
                stage = YandexSyncState.Stage.ERROR,
                statusText = message,
                finished = true,
                success = false,
                error = message
            )
        }
        showFinalNotification("Синхронизация прервана", message)
    }

    private fun handleCancellation() {
        YandexSyncState.update {
            it.copy(
                isRunning = false,
                stage = YandexSyncState.Stage.IDLE,
                statusText = "Синхронизация отменена пользователем",
                finished = true,
                success = false,
                error = "Синхронизация отменена"
            )
        }
        showFinalNotification("Синхронизация отменена", "Операция была отменена пользователем.")
    }

    private fun showFinalNotification(title: String, text: String) {
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(false)
            .setAutoCancel(true)
            .setProgress(0, 0, false)
            .setContentIntent(getMainActivityPendingIntent())
            .build()
        notificationManager.notify(notificationId, notification)
    }

    private fun getMainActivityPendingIntent(): PendingIntent {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(applicationContext, 0, intent, flags)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Синхронизация",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "SyncWorker"
    }
}
