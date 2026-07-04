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

        val scanner = BookScanner(applicationContext)

        try {
            val result = scanner.scanFolders { current, total, currentFileName ->
                // Update SharedPreferences and state
                val msg = "Обработка: $current из $total"
                BookScanState.updateScanning(
                    context = applicationContext,
                    active = true,
                    text = msg,
                    total = total,
                    processed = current
                )
                // Update status bar notification
                ScanNotificationHelper.showScanningNotification(
                    context = applicationContext,
                    total = total,
                    processed = current,
                    currentFileName = currentFileName
                )
            }

            // Handle scan results
            when (result) {
                is ScanResult.Success -> {
                    val finishMsg = "Сканирование завершено. Добавлено ${result.addedCount} книг, пропущено ${result.skippedCount} дубликатов."
                    BookScanState.updateScanning(
                        context = applicationContext,
                        active = false,
                        text = finishMsg,
                        total = BookScanState.totalFiles.value,
                        processed = BookScanState.processedFiles.value
                    )
                    ScanNotificationHelper.showFinishedNotification(
                        context = applicationContext,
                        title = "Сканирование завершено",
                        message = "Добавлено ${result.addedCount} книг, пропущено ${result.skippedCount} дубликатов"
                    )
                }
                is ScanResult.NoBooksFound -> {
                    val emptyMsg = "Книги не найдены. Проверьте папки Downloads, Documents, Books."
                    BookScanState.updateScanning(
                        context = applicationContext,
                        active = false,
                        text = emptyMsg,
                        total = 0,
                        processed = 0
                    )
                    ScanNotificationHelper.showFinishedNotification(
                        context = applicationContext,
                        title = "Книги не найдены",
                        message = "Проверьте папки Downloads, Documents, Books"
                    )
                }
                is ScanResult.Error -> {
                    val errMsg = "Ошибка сканирования: ${result.message}"
                    BookScanState.updateScanning(
                        context = applicationContext,
                        active = false,
                        text = errMsg,
                        total = 0,
                        processed = 0,
                        error = result.message
                    )
                    ScanNotificationHelper.showFinishedNotification(
                        context = applicationContext,
                        title = "Ошибка сканирования",
                        message = result.message
                    )
                }
            }
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
