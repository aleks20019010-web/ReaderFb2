package com.nightread.app.service

import android.content.Context
import androidx.work.*
import com.nightread.app.data.SettingsManager
import com.nightread.app.data.YandexDiskManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object AutoSyncScheduler {
    fun scheduleAutoSync(context: Context) {
        try {
            val workManager = WorkManager.getInstance(context)
            workManager.cancelUniqueWork("YandexAutoSyncWork")

            if (!SettingsManager.isAutoSyncEnabled(context)) {
                return
            }

            val days = SettingsManager.getAutoSyncIntervalDays(context)
            val startTime = SettingsManager.getAutoSyncStartTime(context)

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val initialDelayMs = calculateInitialDelay(startTime, days, context)

            // Using OneTimeWorkRequest to ensure exact timing every time
            val workRequest = OneTimeWorkRequestBuilder<AutoSyncWorker>()
                .setConstraints(constraints)
                .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
                .addTag("YandexAutoSyncWork")
                .build()

            workManager.enqueueUniqueWork(
                "YandexAutoSyncWork",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        } catch (e: Exception) {
            android.util.Log.e("AutoSyncScheduler", "Error scheduling auto sync", e)
        }
    }

    private fun calculateInitialDelay(timeStr: String, intervalDays: Int, context: Context): Long {
        try {
            val parts = timeStr.split(":")
            if (parts.size != 2) return 0L
            val targetHour = parts[0].toIntOrNull() ?: 3
            val targetMinute = parts[1].toIntOrNull() ?: 0

            val lastSync = YandexDiskManager.getLastSyncTimestamp(context)
            val now = Calendar.getInstance()
            val target = Calendar.getInstance()

            if (lastSync > 0L) {
                target.timeInMillis = lastSync
                target.add(Calendar.DAY_OF_YEAR, intervalDays)
            } else {
                target.add(Calendar.DAY_OF_YEAR, intervalDays)
            }
            
            target.set(Calendar.HOUR_OF_DAY, targetHour)
            target.set(Calendar.MINUTE, targetMinute)
            target.set(Calendar.SECOND, 0)
            target.set(Calendar.MILLISECOND, 0)

            if (target.before(now)) {
                target.timeInMillis = now.timeInMillis
                target.set(Calendar.HOUR_OF_DAY, targetHour)
                target.set(Calendar.MINUTE, targetMinute)
                target.set(Calendar.SECOND, 0)
                target.set(Calendar.MILLISECOND, 0)
                if (target.before(now)) {
                    target.add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            return target.timeInMillis - now.timeInMillis
        } catch (e: Exception) {
            return 0L
        }
    }
}
