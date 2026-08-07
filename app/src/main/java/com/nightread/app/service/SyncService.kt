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
import com.nightread.app.data.AppDatabase
import com.nightread.app.data.CloudFileService
import com.nightread.app.data.Sha1Extractor
import com.nightread.app.data.SyncCacheManager
import com.nightread.app.data.SyncOrchestrator
import com.nightread.app.data.SyncProgressTracker
import com.nightread.app.data.SyncSettingsManager
import com.nightread.app.data.YandexSyncState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.sample
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Foreground Service для независимой фоновой синхронизации с Яндекс Диском.
 * Отображает постоянное уведомление в статус-баре с прогрессом, этапом и оставшимся временем.
 * Не прерывается при уничтожении фрагмента или активити.
 */
class SyncService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val mainScope = CoroutineScope(Dispatchers.Main.immediate + serviceJob)
    
    private var syncJob: Job? = null
    private var notificationJob: Job? = null
    private var orchestrator: SyncOrchestrator? = null
    
    private val isSyncRunning = AtomicBoolean(false)
    private var startTime: Long = 0
    
    private lateinit var notificationManager: NotificationManager
    private lateinit var notificationBuilder: NotificationCompat.Builder
    
    private val channelId = "yandex_sync_channel"
    private val notificationId = "YANDEX_SYNC".hashCode()

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        initNotificationBuilder()
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand: action = $action, startId = $startId")

        when (action) {
            ACTION_STOP_SYNC -> {
                stopSync()
                return START_NOT_STICKY
            }
            ACTION_START_SYNC -> {
                // Простая и надёжная защита от повторного запуска
                if (!isSyncRunning.compareAndSet(false, true)) {
                    Log.d(TAG, "Sync is already running, ignoring start request")
                    return START_STICKY
                }
                
                // startForeground ДО создания корутины (важно для Android 12+)
                try {
                    startForegroundServiceCompat()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start foreground service", e)
                    isSyncRunning.set(false)
                    stopSelf()
                    return START_NOT_STICKY
                }
                
                // Запускаем синхронизацию после успешного foreground
                startSync()
            }
            else -> Log.w(TAG, "Unknown action: $action")
        }

        return START_STICKY
    }

    private fun startSync() {
        startTime = System.currentTimeMillis()
        Log.d(TAG, "Starting sync...")

        // Запускаем сбор состояния для уведомлений с debounce (sample)
        notificationJob = mainScope.launch {
            try {
                YandexSyncState.state
                    .sample(150) // 150ms для баланса между отзывчивостью и производительностью
                    .distinctUntilChanged()
                    .collect { state ->
                        if (state.isRunning) {
                            updateNotification(state)
                        }
                    }
            } catch (e: CancellationException) {
                Log.d(TAG, "Notification collection cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Notification collection error", e)
            }
        }

        // Запускаем синхронизацию
        syncJob = serviceScope.launch {
            val context = this@SyncService
            
            try {
                // Устанавливаем флаг синхронизации
                SyncSettingsManager.setSyncing(context, true)
                
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

                // Запускаем синхронизацию (в IO диспетчере)
                runCatching {
                    orch.sync()
                }.onFailure { error ->
                    when (error) {
                        is CancellationException -> {
                            Log.d(TAG, "Sync cancelled by user")
                        }
                        is IOException -> {
                            Log.e(TAG, "IO error during sync", error)
                        }
                        else -> {
                            Log.e(TAG, "Sync failed with unexpected error", error)
                        }
                    }
                }
                
                Log.d(TAG, "Sync completed")

            } finally {
                // Очищаем ресурсы на Main потоке
                withContext(Dispatchers.Main) {
                    cleanup()
                }
            }
        }
    }

    private fun stopSync() {
        Log.d(TAG, "Stopping sync...")
        
        if (!isSyncRunning.get()) {
            Log.d(TAG, "Sync not running, ignoring stop")
            return
        }
        
        orchestrator?.isCancelled = true
        // Отменяем синхронизацию через корутины
        syncJob?.cancel()
    }

    private fun cleanup() {
        Log.d(TAG, "Cleaning up sync resources")
        
        // Отменяем сбор уведомлений
        notificationJob?.cancel()
        notificationJob = null
        
        // Сбрасываем флаги
        isSyncRunning.set(false)
        SyncSettingsManager.setSyncing(this, false)
        
        // Очищаем ссылки
        orchestrator = null
        syncJob = null
        
        // Логируем время
        val duration = System.currentTimeMillis() - startTime
        Log.d(TAG, "Sync finished in ${duration}ms")
        
        // Останавливаем foreground и сервис
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundServiceCompat() {
        val notification = notificationBuilder
            .setContentTitle(getString(R.string.sync_title))
            .setContentText(getString(R.string.sync_starting))
            .setProgress(100, 0, true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                notificationId,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(notificationId, notification)
        }
    }

    private fun updateNotification(state: YandexSyncState) {
        if (!state.isRunning) return

        val title = getString(R.string.sync_progress, state.percent)
        
        var contentText = state.statusText
        if (state.stage == YandexSyncState.Stage.DOWNLOADING || 
            state.stage == YandexSyncState.Stage.UPLOADING) {
            state.currentFileName?.let { fileName ->
                contentText = "${state.statusText}: $fileName"
            }
        }

        val max = if (state.total > 0) state.total else 100
        val progress = if (state.total > 0) state.completed else 0
        val isIndeterminate = state.stage == YandexSyncState.Stage.SCANNING || 
                             state.stage == YandexSyncState.Stage.PREPARING

        try {
            notificationBuilder
                .setContentTitle(title)
                .setContentText(contentText)
                .setProgress(max, progress, isIndeterminate)
            
            notificationManager.notify(notificationId, notificationBuilder.build())
        } catch (e: SecurityException) {
            Log.e(TAG, "Notification permission missing", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating notification", e)
        }
    }

    private fun initNotificationBuilder() {
        notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(getMainActivityPendingIntent())
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
                getString(R.string.sync_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = getString(R.string.sync_channel_description)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroying")
        
        // Отменяем все корутины
        notificationJob?.cancel()
        syncJob?.cancel()
        serviceJob.cancel()
        mainScope.cancel()
        
        // Очищаем ресурсы
        orchestrator = null
        
        // Сбрасываем флаг
        isSyncRunning.set(false)
        SyncSettingsManager.setSyncing(this, false)
        
        // Убираем уведомление
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "SyncService"
        const val ACTION_START_SYNC = "com.nightread.app.action.START_SYNC"
        const val ACTION_STOP_SYNC = "com.nightread.app.action.STOP_SYNC"
    }
}
