package com.example.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class BookScanWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("BookScanWorker", "Scanning started in background")
        
        // Mark scanning as active with initial message
        BookScanState.updateScanning(
            context = applicationContext,
            active = true,
            text = "Поиск файлов...",
            total = 0,
            processed = 0
        )

        // Show starting notification
        ScanNotificationHelper.showScanningNotification(
            context = applicationContext,
            total = 0,
            processed = 0,
            currentFileName = "Подготовка к поиску файлов..."
        )

        val scanner = NewBookScanner(applicationContext, com.example.data.AppDatabase.getDatabase(applicationContext).bookDao())

        try {
            scanner.scan()
            
            // Handle scan results
            val finalState = scanner.state.value
            val finishMsg = "Scan finished. Added ${finalState.addedBooks} books, skipped ${finalState.skippedBooks} duplicates."
            
            BookScanState.updateScanning(
                context = applicationContext,
                active = false,
                text = finishMsg,
                total = finalState.totalFiles,
                processed = finalState.processedFiles
            )
            ScanNotificationHelper.showFinishedNotification(
                context = applicationContext,
                title = "Scan finished",
                message = "Added ${finalState.addedBooks} books, skipped ${finalState.skippedBooks} duplicates"
            )
        } catch (e: Exception) {
            Log.e("BookScanWorker", "Critical exception during scan", e)
            val errorText = e.localizedMessage ?: "Неизвестная ошибка"
            val errMsg = "Ошибка сканирования: $errorText"
            
            BookScanState.updateScanning(
                context = applicationContext,
                active = false,
                text = errMsg,
                total = 0,
                processed = 0,
                error = errorText
            )
            ScanNotificationHelper.showFinishedNotification(
                context = applicationContext,
                title = "Ошибка сканирования",
                message = errorText
            )
            return Result.failure()
        }

        return Result.success()
    }
}
