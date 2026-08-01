package com.nightread.app.service

import android.content.Context
import androidx.work.*
import com.nightread.app.data.SettingsManager
import com.nightread.app.data.YandexDiskManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object AutoSyncScheduler {
    fun scheduleAutoSync(context: Context, forceReplace: Boolean = true) {
        try {
            val workManager = WorkManager.getInstance(context)

            if (!SettingsManager.isAutoSyncEnabled(context)) {
                workManager.cancelUniqueWork("YandexAutoSyncWork")
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

            val policy = if (forceReplace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP

            workManager.enqueueUniqueWork(
                "YandexAutoSyncWork",
                policy,
                workRequest
            )
            android.util.Log.d("AutoSyncScheduler", "Auto sync scheduled in ${initialDelayMs / 1000 / 60} minutes, policy = $policy")
        } catch (e: Exception) {
            android.util.Log.e("AutoSyncScheduler", "Error scheduling auto sync", e)
        }
    }

    fun scheduleRetryAfterFailure(context: Context) {
        try {
            if (!SettingsManager.isAutoSyncEnabled(context)) return
            val workManager = WorkManager.getInstance(context)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val workRequest = OneTimeWorkRequestBuilder<AutoSyncWorker>()
                .setConstraints(constraints)
                .setInitialDelay(30, TimeUnit.MINUTES)
                .addTag("YandexAutoSyncWork")
                .build()

            workManager.enqueueUniqueWork(
                "YandexAutoSyncWork",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
            android.util.Log.d("AutoSyncScheduler", "Auto sync retry scheduled in 30 minutes due to previous error")
        } catch (e: Exception) {
            android.util.Log.e("AutoSyncScheduler", "Error scheduling auto sync retry", e)
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
            val nowMs = now.timeInMillis
            val intervalMs = intervalDays * 24 * 60 * 60 * 1000L

            // If never synced before or if more than intervalDays have passed since lastSync -> OVERDUE -> Run NOW
            if (lastSync == 0L || (nowMs - lastSync) >= intervalMs) {
                return 0L
            }

            val target = Calendar.getInstance()
            target.timeInMillis = lastSync
            target.add(Calendar.DAY_OF_YEAR, intervalDays)
            target.set(Calendar.HOUR_OF_DAY, targetHour)
            target.set(Calendar.MINUTE, targetMinute)
            target.set(Calendar.SECOND, 0)
            target.set(Calendar.MILLISECOND, 0)

            while (target.before(now)) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }

            val delay = target.timeInMillis - nowMs
            return if (delay < 0L) 0L else delay
        } catch (e: Exception) {
            return 0L
        }
    }
}
