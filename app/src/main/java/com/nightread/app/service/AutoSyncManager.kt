package com.nightread.app.service

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.Calendar
import java.util.concurrent.TimeUnit

object AutoSyncManager {
    private const val TAG = "AutoSyncManager"
    private const val PREFS_NAME = "sync_settings_prefs"
    
    private const val KEY_AUTO_SYNC_ENABLED = "auto_sync_enabled"
    private const val KEY_AUTO_SYNC_INTERVAL = "auto_sync_interval"
    private const val KEY_AUTO_SYNC_TIME = "auto_sync_time"
    private const val KEY_AUTO_SYNC_WIFI_ONLY = "auto_sync_wifi_only"
    
    private const val UNIQUE_WORK_NAME = "YandexAutoSyncWork"

    fun isAutoSyncEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_SYNC_ENABLED, false)
    }

    fun setAutoSyncEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTO_SYNC_ENABLED, enabled).apply()
        if (enabled) {
            scheduleAutoSync(context)
        } else {
            cancelAutoSync(context)
        }
    }

    fun getInterval(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_AUTO_SYNC_INTERVAL, "1_DAY") ?: "1_DAY"
    }

    fun setInterval(context: Context, interval: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_AUTO_SYNC_INTERVAL, interval).apply()
        if (isAutoSyncEnabled(context)) {
            scheduleAutoSync(context)
        }
    }

    fun getStartTime(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_AUTO_SYNC_TIME, "00:00") ?: "00:00"
    }

    fun setStartTime(context: Context, time: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_AUTO_SYNC_TIME, time).apply()
        if (isAutoSyncEnabled(context)) {
            scheduleAutoSync(context)
        }
    }

    fun isWifiOnly(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_SYNC_WIFI_ONLY, false)
    }

    fun setWifiOnly(context: Context, wifiOnly: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTO_SYNC_WIFI_ONLY, wifiOnly).apply()
        if (isAutoSyncEnabled(context)) {
            scheduleAutoSync(context)
        }
    }

    fun scheduleAutoSync(context: Context) {
        try {
            val workManager = WorkManager.getInstance(context)
            
            val intervalStr = getInterval(context)
            val startTimeStr = getStartTime(context)
            val wifiOnly = isWifiOnly(context)

            // Convert interval to hours
            val intervalHours = when (intervalStr) {
                "6_HOURS" -> 6L
                "12_HOURS" -> 12L
                "1_DAY" -> 24L
                "3_DAYS" -> 72L
                else -> 24L
            }

            // Constraints
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .build()

            // Calculate initial delay based on chosen start time
            val initialDelayMs = calculateInitialDelay(startTimeStr)

            val workRequest = PeriodicWorkRequestBuilder<SyncWorker>(intervalHours, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("is_auto" to true))
                .addTag(UNIQUE_WORK_NAME)
                .build()

            workManager.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
            Log.d(TAG, "Scheduled auto sync: interval = $intervalHours hours, start time = $startTimeStr, wifiOnly = $wifiOnly, initial delay = ${initialDelayMs / 1000 / 60} mins")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling auto sync", e)
        }
    }

    fun cancelAutoSync(context: Context) {
        try {
            val workManager = WorkManager.getInstance(context)
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            Log.d(TAG, "Cancelled auto sync work.")
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling auto sync", e)
        }
    }

    private fun calculateInitialDelay(timeStr: String): Long {
        try {
            val parts = timeStr.split(":")
            if (parts.size != 2) return 0L
            val targetHour = parts[0].toIntOrNull() ?: 0
            val targetMinute = parts[1].toIntOrNull() ?: 0

            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, targetHour)
                set(Calendar.MINUTE, targetMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (target.before(now)) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }

            return target.timeInMillis - now.timeInMillis
        } catch (e: Exception) {
            Log.e(TAG, "Failed to calculate initial delay for $timeStr", e)
            return 0L
        }
    }
}
