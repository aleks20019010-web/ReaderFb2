package com.nightread.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters

/**
 * Фоновый воркер для выполнения синхронизации с Яндекс Диском без блокировки UI.
 * Работает как Foreground Service (с уведомлением в статус-баре), позволяя продолжать
 * работу даже при сворачивании приложения.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "yandex_sync_channel"
    private val notificationId = 2002

    override suspend fun doWork(): Result {
        Log.d("SyncWorker", "SyncWorker started execution")
        createNotificationChannel()
        
        // Переводим воркер в режим Foreground
        try {
            setForeground(getForegroundInfo())
        } catch (e: Exception) {
            Log.e("SyncWorker", "Could not set worker to foreground", e)
        }

        // Сбрасываем и устанавливаем начальное состояние
        YandexSyncState.update {
            it.copy(
                isRunning = true,
                stage = YandexSyncState.Stage.PREPARING,
                statusText = "Подготовка к синхронизации...",
                finished = false,
                success = false,
                error = null
            )
        }

        val syncManager = YandexSyncManager(applicationContext)

        // 1. ЭТАП: АНАЛИЗ ДИСКА
        YandexSyncState.update { 
            it.copy(
                stage = YandexSyncState.Stage.SCANNING, 
                statusText = "Анализ диска..."
            ) 
        }
        updateNotification("Анализ диска...", 0, 100, true)

        val stats = syncManager.calculateSyncStats { progressText ->
            YandexSyncState.update { it.copy(statusText = progressText) }
            updateNotification(progressText, 0, 100, true)
        }

        if (stats == null) {
            val errorMsg = "Ошибка при анализе Яндекс Диска. Проверьте авторизацию."
            YandexSyncState.update {
                it.copy(
                    isRunning = false,
                    stage = YandexSyncState.Stage.ERROR,
                    statusText = errorMsg,
                    finished = true,
                    success = false,
                    error = errorMsg
                )
            }
            showFinalNotification("Синхронизация прервана", errorMsg)
            return Result.failure()
        }

        // Проверка отмены перед началом выполнения синхронизации
        if (isStopped) {
            handleCancellation()
            return Result.failure()
        }

        // 2. ЭТАП: НЕПОСРЕДСТВЕННАЯ СИНХРОНИЗАЦИЯ (СКАЧИВАНИЕ И ЗАГРУЗКА)
        val syncSuccess = syncManager.performSync(stats) { status, completed, total, stage, downloaded, uploaded, remaining ->
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

        // Проверка отмены после выполнения синхронизации
        if (isStopped) {
            handleCancellation()
            return Result.failure()
        }

        if (syncSuccess) {
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
                "Скачано: ${YandexSyncState.state.value.downloadedCount}, Загружено: ${YandexSyncState.state.value.uploadedCount}."
            )
            return Result.success()
        } else {
            val errorMsg = "Ошибка во время синхронизации данных."
            YandexSyncState.update {
                it.copy(
                    isRunning = false,
                    stage = YandexSyncState.Stage.ERROR,
                    statusText = errorMsg,
                    finished = true,
                    success = false,
                    error = errorMsg
                )
            }
            showFinalNotification("Синхронизация прервана", errorMsg)
            return Result.failure()
        }
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

    override suspend fun getForegroundInfo(): ForegroundInfo {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Синхронизация с Яндекс Диском")
            .setContentText("Запуск синхронизации...")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, 0, true)
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
            .build()
        notificationManager.notify(notificationId, notification)
    }

    private fun showFinalNotification(title: String, text: String) {
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(false)
            .setAutoCancel(true)
            .setProgress(0, 0, false)
            .build()
        notificationManager.notify(notificationId, notification)
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
}
