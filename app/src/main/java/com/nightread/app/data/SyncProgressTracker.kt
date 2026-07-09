package com.nightread.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.nightread.app.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SyncState(
    val stage: String = "Ожидание",
    val totalFiles: Int = 0,
    val processedFiles: Int = 0,
    val toUpload: Int = 0,
    val toDownload: Int = 0,
    val uploaded: Int = 0,
    val downloaded: Int = 0,
    val message: String = "Готов к синхронизации"
)

class SyncProgressTracker(private val context: Context) {
    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "yandex_sync_channel"
    private val notificationId = 2002

    private var stageStartTime: Long = 0L
    private var timer: java.util.Timer? = null

    init {
        createNotificationChannel()
        startTimer()
    }

    private fun startTimer() {
        timer?.cancel()
        timer = java.util.Timer().apply {
            scheduleAtFixedRate(object : java.util.TimerTask() {
                override fun run() {
                    val currentState = YandexSyncState.state.value
                    if (currentState.isRunning && !currentState.finished && !currentState.success) {
                        val elapsedMs = System.currentTimeMillis() - stageStartTime
                        val total = currentState.total
                        val completed = currentState.completed
                        val remaining = total - completed
                        
                        var remainingTime = -1L
                        if (completed > 0 && elapsedMs > 1000L && remaining >= 0 && total > 0) {
                            val speed = completed.toDouble() / elapsedMs // files per ms
                            if (speed > 0) {
                                remainingTime = (remaining / speed / 1000.0).toLong()
                            }
                        }
                        
                        YandexSyncState.update {
                            it.copy(remainingTimeSeconds = remainingTime)
                        }
                    }
                }
            }, 1000L, 1000L)
        }
    }

    private fun cancelTimer() {
        timer?.cancel()
        timer = null
    }

    fun startStage(stageName: String, total: Int = 0, msg: String = "") {
        stageStartTime = System.currentTimeMillis()
        _state.value = _state.value.copy(
            stage = stageName,
            totalFiles = total,
            processedFiles = 0,
            message = if (msg.isNotEmpty()) msg else stageName
        )
        updateYandexSyncState(stageName, total, 0, if (msg.isNotEmpty()) msg else stageName)
        updateNotification(if (msg.isNotEmpty()) msg else stageName, 0, total.coerceAtLeast(100), total == 0)
    }

    fun updateProgress(processed: Int, total: Int, msg: String = "") {
        _state.value = _state.value.copy(
            totalFiles = total,
            processedFiles = processed,
            message = msg
        )
        updateYandexSyncState(_state.value.stage, total, processed, msg)
        updateNotification(msg, processed, total.coerceAtLeast(1), total == 0)
    }

    fun updateStats(toUpload: Int, toDownload: Int, uploaded: Int, downloaded: Int) {
        _state.value = _state.value.copy(
            toUpload = toUpload,
            toDownload = toDownload,
            uploaded = uploaded,
            downloaded = downloaded
        )
        YandexSyncState.update {
            it.copy(
                uploadedCount = uploaded,
                downloadedCount = downloaded
            )
        }
    }

    fun finishStage(stageName: String, msg: String) {
        _state.value = _state.value.copy(
            stage = stageName,
            message = msg
        )
        updateYandexSyncState(stageName, _state.value.totalFiles, _state.value.processedFiles, msg)
        updateNotification(msg, _state.value.processedFiles, _state.value.totalFiles.coerceAtLeast(1), false)
    }

    private fun updateYandexSyncState(stageName: String, total: Int, completed: Int, msg: String) {
        val uiStage = when (stageName) {
            "Получение списка файлов" -> YandexSyncState.Stage.SCANNING
            "Загрузка временных файлов" -> YandexSyncState.Stage.DOWNLOADING
            "Вычисление SHA-1" -> YandexSyncState.Stage.SCANNING
            "Сравнение с библиотекой" -> YandexSyncState.Stage.PREPARING
            "Загрузка на диск" -> YandexSyncState.Stage.UPLOADING
            "Скачивание с диска" -> YandexSyncState.Stage.DOWNLOADING
            else -> YandexSyncState.Stage.PROGRESS_SYNC
        }

        YandexSyncState.update {
            it.copy(
                isRunning = true,
                stage = uiStage,
                statusText = msg,
                completed = completed,
                total = total,
                percent = if (total > 0) (completed * 100) / total else 0
            )
        }
    }

    fun showFinalNotification(title: String, text: String, success: Boolean) {
        cancelTimer()
        val notification = NotificationCompat.Builder(context, channelId)
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

    private fun updateNotification(text: String, progress: Int, max: Int, indeterminate: Boolean) {
        val notification = NotificationCompat.Builder(context, channelId)
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

    private fun getMainActivityPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(context, 0, intent, flags)
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
