package com.nightread.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.nightread.app.R
import com.nightread.app.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

class AutoDiscoveryService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val observers = mutableListOf<FileObserver>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NightRead")
            .setContentText("Авто-обнаружение книг включено")
            .setSmallIcon(R.drawable.ic_custom_list)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e("AutoDiscoveryService", "Failed to startForeground", e)
        }
        setupObservers()
    }

    private fun setupObservers() {
        val paths = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            File(Environment.getExternalStorageDirectory(), "Books")
        )

        for (path in paths) {
            if (path.exists() && path.isDirectory) {
                // We use a custom subclass because FileObserver is deprecated in higher APIs,
                // but we will use the standard one for compatibility
                val observer = object : FileObserver(path.absolutePath, CREATE or MOVED_TO) {
                    override fun onEvent(event: Int, pathString: String?) {
                        if (pathString != null) {
                            val file = File(path, pathString)
                            if (file.isFile && com.nightread.app.data.BookFormatHelper.isSupported(file.absolutePath)) {
                                Log.d("AutoDiscoveryService", "New file detected: ${file.absolutePath}")
                                handleNewFile(file)
                            }
                        }
                    }
                }
                observer.startWatching()
                observers.add(observer)
            }
        }
    }

    private fun handleNewFile(file: File) {
        if (AutoDiscoveryService.isManualScanning) {
            Log.d("AutoDiscoveryService", "Manual scan in progress, skipping new file: ${file.name}")
            return
        }
        serviceScope.launch {
            try {
                // Small delay to make sure file is fully written
                kotlinx.coroutines.delay(2000)
                val bookDao = AppDatabase.getDatabase(this@AutoDiscoveryService).bookDao()
                val scanner = com.nightread.app.scanner.LibraryScanner(this@AutoDiscoveryService, bookDao)
                
                val initialCount = bookDao.getSha1ToPathMap().size
                scanner.scanBooks().join()
                val newCount = bookDao.getSha1ToPathMap().size
                
                val added = newCount - initialCount
                if (added > 0) {
                    showNewBooksNotification(added)
                }
            } catch (e: Exception) {
                Log.e("AutoDiscoveryService", "Error scanning new file", e)
            }
        }
    }

    private fun showNewBooksNotification(addedCount: Int) {
        // Disabled as requested
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        for (observer in observers) {
            observer.stopWatching()
        }
        observers.clear()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Auto Discovery Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "AutoDiscoveryChannel"
        private const val NOTIFICATION_ID = 101

        var isManualScanning = false

        fun start(context: Context) {
            try {
                val intent = Intent(context, AutoDiscoveryService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e("AutoDiscoveryService", "Failed to start service", e)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AutoDiscoveryService::class.java)
            context.stopService(intent)
        }
    }
}
