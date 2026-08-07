package com.nightread.app.scanner

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nightread.app.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SyncWorker"
        const val KEY_SCAN_PATH = "scan_path"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "SyncWorker started background book scan and duplicate check.")
        try {
            val database = AppDatabase.getDatabase(applicationContext)
            val bookDao = database.bookDao()
            val bookCacheDao = database.bookCacheDao()
            val scanner = LibraryScanner(applicationContext, bookDao)
            val duplicateFinder = DuplicateFinder(bookCacheDao)

            // Определяем целевую директорию для сканирования из входных данных или директории приложения по умолчанию
            val customPath = inputData.getString(KEY_SCAN_PATH)
            val scanDir = if (!customPath.isNullOrBlank()) {
                File(customPath)
            } else {
                AppDatabase.getAppDir(applicationContext)
            }

            Log.d(TAG, "Target scan directory: ${scanDir.absolutePath}")

            if (scanDir.exists() && scanDir.isDirectory) {
                scanner.scanBooks().join()
                Log.d(TAG, "Background scan finished")

                val duplicates = duplicateFinder.findDuplicates()
                Log.d(TAG, "Found ${duplicates.size} duplicate groups.")

                for ((fingerprint, group) in duplicates) {
                    Log.d(TAG, "Duplicate group [$fingerprint]: ${group.map { it.path }}")
                }
            } else {
                Log.w(TAG, "Scan directory does not exist or is not a directory: ${scanDir.absolutePath}")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in SyncWorker background scan execution", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
