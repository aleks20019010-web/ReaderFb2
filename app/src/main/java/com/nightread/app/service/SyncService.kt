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
import com.nightread.app.data.*
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
    private var orchestrator: SyncOrchestrator? = null

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
        startForegroundServiceCompat()

        // Launch a coroutine to update the notification dynamically based on sync state
        serviceScope.launch {
            YandexSyncState.state.collect { state ->
                updateNotification(state)
            }
        }

        syncJob = serviceScope.launch {
            val context = applicationContext
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
            orchestrator = orch

            try {
                // Set the isSyncing flag before running synchronization
                com.nightread.app.data.SyncSettingsManager.setSyncing(context, true)
                withContext(Dispatchers.IO) {
                    orch.sync()
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "SyncService sync cancelled")
            } catch (e: Exception) {
                Log.e("SYNC_ERROR", "SyncService sync failed", e)
            } finally {
                // Reset isSyncing flag to false
                com.nightread.app.data.SyncSettingsManager.setSyncing(context, false)
                stopSelf()
            }
        }
    }

    private fun stopSync() {
        Log.d(TAG, "Stopping sync service...")
        orchestrator?.isCancelled = true
        syncJob?.cancel()
        stopSelf()
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

    private fun updateNotification(state: YandexSyncState) {
        if (!state.isRunning) return

        val title = "Синхронизация (${state.percent}%)"
        
        var contentText = state.statusText
        if (state.stage == YandexSyncState.Stage.DOWNLOADING || state.stage == YandexSyncState.Stage.UPLOADING) {
            if (state.currentFileName != null) {
                contentText = "${state.statusText}: ${state.currentFileName}"
            }
        }

        val max = if (state.total > 0) state.total else 100
        val progress = if (state.total > 0) state.completed else 0
        val isIndeterminate = state.stage == YandexSyncState.Stage.SCANNING || state.stage == YandexSyncState.Stage.PREPARING

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(max, progress, isIndeterminate)
            .setContentIntent(getMainActivityPendingIntent())
            .build()
            
        try {
            notificationManager.notify(notificationId, notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "Notification permission missing", e)
        }
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
