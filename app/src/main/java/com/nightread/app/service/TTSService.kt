package com.nightread.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.nightread.app.R

class TTSService : Service() {
    private val binder = LocalBinder()
    private var ttsManager: TTSManager? = null
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "TTS_SERVICE_CHANNEL"

    inner class LocalBinder : Binder() {
        fun getService(): TTSService = this@TTSService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        ttsManager = TTSManager(this, { }, null)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "TTS Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    fun startForegroundPlayback() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NightRead TTS")
            .setContentText("Читаем книгу...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    fun stopForegroundPlayback() {
        stopForeground(true)
    }

    fun getTtsManager(): TTSManager? = ttsManager

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        ttsManager?.shutdown()
        super.onDestroy()
    }
}
