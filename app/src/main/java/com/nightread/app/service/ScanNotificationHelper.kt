package com.nightread.app.service

import android.content.Context

object ScanNotificationHelper {

    fun createNotificationChannel(context: Context) {}

    fun showScanningNotification(context: Context, total: Int, processed: Int, currentFileName: String) {}

    fun showFinishedNotification(context: Context, title: String, message: String) {}

    fun cancelNotification(context: Context) {}
}
