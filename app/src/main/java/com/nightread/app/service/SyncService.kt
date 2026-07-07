package com.nightread.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.nightread.app.MainActivity
import com.nightread.app.R
import com.nightread.app.data.YandexSyncManager
import com.nightread.app.data.YandexSyncState
import kotlinx.coroutines.*

/**
 * Foreground Service для независимой фоновой синхронизации с Яндекс Диском.
 * Отображает постоянное уведомление в статус-баре с прогрессом, этапом и оставшимся временем.
 * Не прерывается при уничтожении фрагмента или активити.
 */
class SyncService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var syncJob: Job? = null

    private lateinit var notificationManager: NotificationManager
    private val channelId = "yandex_sync_channel"
    private val notificationId = 2002

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand: action = $action")

        if (action == ACTION_STOP_SYNC) {
            stopSync()
            return START_NOT_STICKY
        }

        if (action == ACTION_START_SYNC) {
            if (YandexSyncState.state.value.isRunning) {
                Log.d(TAG, "Sync is already running, ignoring start request")
                return START_STICKY
            }
            startSync()
        }

        return START_STICKY
    }

    private fun startSync() {
        // 1. Показываем начальное уведомление и запускаем Foreground
        startForegroundServiceCompat()

        // 2. Сбрасываем и инициализируем состояние
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

        // 3. Запускаем корутину фоновой работы
        syncJob = serviceScope.launch {
            val context = applicationContext
            val syncManager = YandexSyncManager(context)

            try {
                // ЭТАП 1: Анализ диска
                YandexSyncState.update {
                    it.copy(
                        stage = YandexSyncState.Stage.SCANNING,
                        statusText = "Анализ Яндекс Диска..."
                    )
                }
                updateNotification("Анализ диска...", 0, 100, true)

                val stats = withContext(Dispatchers.IO) {
                    syncManager.calculateSyncStats { progressText ->
                        YandexSyncState.update { it.copy(statusText = progressText) }
                        updateNotification(progressText, 0, 100, true)
                    }
                }

                if (stats == null) {
                    val errorMsg = "Ошибка при анализе Яндекс Диска. Проверьте авторизацию."
                    handleError(errorMsg)
                    return@launch
                }

                ensureActive()

                // ЭТАП 2: Непосредственная синхронизация (Скачивание и Загрузка)
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
                } else {
                    handleError("Ошибка во время синхронизации данных.")
                }

            } catch (e: CancellationException) {
                handleCancellation()
            } catch (e: Exception) {
                Log.e(TAG, "Error in SyncService execution", e)
                handleError(e.localizedMessage ?: "Неизвестная ошибка")
            } finally {
                stopSelf()
            }
        }
    }

    private fun stopSync() {
        Log.d(TAG, "Stopping sync service...")
        syncJob?.cancel()
        handleCancellation()
        stopSelf()
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

    private fun startForegroundServiceCompat() {
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Синхронизация с Яндекс Диском")
            .setContentText("Запуск фоновой синхронизации...")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, 0, true)
            .setContentIntent(getMainActivityPendingIntent())
            .build()

        startForeground(notificationId, notification)
    }

    private fun updateNotification(text: String, progress: Int, max: Int, indeterminate: Boolean) {
        val notification = NotificationCompat.Builder(this, channelId)
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

    private fun showFinalNotification(title: String, text: String) {
        val notification = NotificationCompat.Builder(this, channelId)
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
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(this, 0, intent, flags)
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

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "SyncService"
        const val ACTION_START_SYNC = "com.nightread.app.action.START_SYNC"
        const val ACTION_STOP_SYNC = "com.nightread.app.action.STOP_SYNC"
    }
}
