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
        NewBookScanState.updateState(ScannerState(isScanning = true, status = "Searching files..."))

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
            
            NewBookScanState.updateState(finalState.copy(isScanning = false, status = finishMsg))
            ScanNotificationHelper.showFinishedNotification(
                context = applicationContext,
                title = "Scan finished",
                message = "Added ${finalState.addedBooks} books, skipped ${finalState.skippedBooks} duplicates"
            )
        } catch (e: Exception) {
            Log.e("BookScanWorker", "Critical exception during scan", e)
            val errorText = e.localizedMessage ?: "Unknown error"
            val errMsg = "Error: $errorText"
            
            NewBookScanState.updateState(ScannerState(isScanning = false, status = errMsg))
            ScanNotificationHelper.showFinishedNotification(
                context = applicationContext,
                title = "Scan Error",
                message = errorText
            )
            return Result.failure()
        }

        return Result.success()
    }
}
