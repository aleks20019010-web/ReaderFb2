package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity

object ScanNotificationHelper {

    private const val CHANNEL_ID = "book_scan_channel"
    private const val NOTIFICATION_ID = 404

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Сканирование книг"
            val descriptionText = "Отображает ход фонового сканирования папок"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showScanningNotification(context: Context, total: Int, processed: Int, currentFileName: String) {
        createNotificationChannel(context)
        
        val progressText = if (total > 0) {
            "Обработано $processed из $total файлов"
        } else {
            "Поиск книг..."
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_rotate)
            .setContentTitle("Идёт сканирование папок: Downloads, Documents, Books...")
            .setContentText(progressText)
            .setSubText(currentFileName)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (total > 0) {
            builder.setProgress(total, processed, false)
        } else {
            builder.setProgress(100, 0, true)
        }

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            android.util.Log.e("ScanNotificationHelper", "Permission for notifications not granted yet", e)
        }
    }

    fun showFinishedNotification(context: Context, title: String, message: String) {
        createNotificationChannel(context)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setOngoing(false)

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.cancel(NOTIFICATION_ID)
            notificationManager.notify(NOTIFICATION_ID + 1, builder.build())
        } catch (e: SecurityException) {
            android.util.Log.e("ScanNotificationHelper", "Permission for notifications not granted yet", e)
        }
    }

    fun cancelNotification(context: Context) {
        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            android.util.Log.e("ScanNotificationHelper", "Failed to cancel notification", e)
        }
    }
}
