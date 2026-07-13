package com.nightread.app.syncprogress

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

private const val TAG = "SyncProgressWorker"

class SyncProgressWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "Background progress sync worker started execution...")
        
        val prefs = applicationContext.getSharedPreferences("yandex_sync_prefs", Context.MODE_PRIVATE)
        val token = prefs.getString("oauth_token", "") ?: ""
        val accountId = prefs.getString("account_id", "") ?: ""
        
        if (token.isBlank() || accountId.isBlank()) {
            Log.w(TAG, "OAuth token or Account ID is blank. Aborting background sync worker.")
            return Result.failure()
        }

        val db = SyncDatabase.getDatabase(applicationContext)
        val yandexDiskApi = retrofit2.Retrofit.Builder()
            .baseUrl("https://cloud-api.yandex.net/")
            .addConverterFactory(retrofit2.converter.moshi.MoshiConverterFactory.create())
            .build()
            .create(com.nightread.app.data.YandexDiskApi::class.java)

        val sha1CacheRepository = Sha1CacheRepository(applicationContext, db.syncSha1CacheDao(), yandexDiskApi)
        val progressRepository = ProgressRepository(db.syncReadingProgressDao(), db.syncBookDao())
        val cloudSyncRepository = CloudSyncRepository(applicationContext, yandexDiskApi)
        val useCase = SyncReadingProgressUseCase(sha1CacheRepository, progressRepository, cloudSyncRepository)

        return try {
            val progressList = progressRepository.getAllLocalProgress()
            if (progressList.isEmpty()) {
                Log.d(TAG, "No local progress entries found to sync.")
                return Result.success()
            }

            for (progress in progressList) {
                val book = progressRepository.findBookBySha1(progress.bookId) ?: continue
                
                useCase.syncBookProgress(
                    token = token,
                    accountId = accountId,
                    cloudPath = book.path,
                    fileSize = book.size,
                    fileModified = book.modified
                )
            }
            Log.i(TAG, "Background progress sync finished successfully.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Background progress sync worker failed", e)
            Result.retry()
        }
    }

    companion object {
        /**
         * Метод планирования фоновой ежечасной синхронизации прогресса чтения.
         */
        fun scheduleHourlySync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<SyncProgressWorker>(
                1, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "HourlyProgressSync",
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
            Log.i(TAG, "Hourly background progress sync scheduled.")
        }
    }
}
