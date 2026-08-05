package com.nightread.app.scanner

import android.util.Log
import com.nightread.app.data.BookCache
import com.nightread.app.data.BookCacheDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

class SyncManager(
    private val bookCacheDao: BookCacheDao,
    private val batchSize: Int = 30,
    private val maxConcurrency: Int = 3
) {

    companion object {
        private const val TAG = "SyncManager"
    }

    private val semaphore = Semaphore(maxConcurrency)

    data class SyncManagerResult(
        val totalProcessed: Int,
        val savedCount: Int,
        val duplicatesSkipped: Int,
        val errorsCount: Int
    )

    /**
     * Пакетная обработка временных файлов, полученных при синхронизации с облаком или ПК.
     *
     * @param tempFiles Список скачанных временных файлов.
     * @param targetDirectory Директория постоянного хранения книг библиотеки.
     */
    suspend fun processIncomingTempFiles(
        tempFiles: List<File>,
        targetDirectory: File
    ): SyncManagerResult = withContext(Dispatchers.IO) {
        if (!targetDirectory.exists()) {
            targetDirectory.mkdirs()
        }

        var savedCount = 0
        var duplicatesSkipped = 0
        var errorsCount = 0

        Log.d(TAG, "Starting processing of ${tempFiles.size} incoming temporary files in batches of $batchSize.")

        tempFiles.chunked(batchSize).forEach { chunk ->
            val newCachesToInsert = mutableListOf<BookCache>()

            coroutineScope {
                val tasks = chunk.map { tempFile ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            processTempFile(tempFile, targetDirectory)
                        }
                    }
                }

                val results = tasks.awaitAll()
                for (res in results) {
                    when (res) {
                        is TempFileResult.Saved -> {
                            savedCount++
                            newCachesToInsert.add(res.bookCache)
                        }
                        is TempFileResult.Duplicate -> {
                            duplicatesSkipped++
                        }
                        is TempFileResult.Error -> {
                            errorsCount++
                        }
                    }
                }
            }

            // Пакетное сохранение новых уникальных файлов в БД
            if (newCachesToInsert.isNotEmpty()) {
                try {
                    bookCacheDao.insertAll(newCachesToInsert)
                    Log.d(TAG, "Saved batch of ${newCachesToInsert.size} new books to database.")
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving batch to database", e)
                }
            }
        }

        Log.d(TAG, "Sync processing completed. Total: ${tempFiles.size}, Saved: $savedCount, Duplicates skipped: $duplicatesSkipped, Errors: $errorsCount")

        SyncManagerResult(
            totalProcessed = tempFiles.size,
            savedCount = savedCount,
            duplicatesSkipped = duplicatesSkipped,
            errorsCount = errorsCount
        )
    }

    private sealed class TempFileResult {
        data class Saved(val bookCache: BookCache) : TempFileResult()
        object Duplicate : TempFileResult()
        object Error : TempFileResult()
    }

    /**
     * Обработка одного временного файла:
     * 1. Генерация отпечатка (fingerprint)
     * 2. Проверка наличия в БД по fingerprint
     * 3. Если дубликат — удаление временного файла
     * 4. Если уникальный — перемещение в targetDirectory и сохранение
     */
    private suspend fun processTempFile(
        tempFile: File,
        targetDirectory: File
    ): TempFileResult {
        if (!tempFile.exists() || tempFile.length() == 0L) {
            Log.w(TAG, "Temp file does not exist or is empty: ${tempFile.name}")
            try { tempFile.delete() } catch (_: Exception) {}
            return TempFileResult.Error
        }

        return try {
            val fingerprintResult = BookFingerprintGenerator.generate(tempFile)
            val existingMatches = bookCacheDao.getByFingerprint(fingerprintResult.fingerprint)

            if (existingMatches.isNotEmpty()) {
                // Найден дубликат! Удаляем временный файл
                Log.d(TAG, "Duplicate detected for temp file ${tempFile.name} (Fingerprint: ${fingerprintResult.fingerprint}). Deleting temp file.")
                try {
                    tempFile.delete()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to delete temp duplicate file: ${tempFile.absolutePath}", e)
                }
                TempFileResult.Duplicate
            } else {
                // Уникальный файл — перемещаем в целевую папку
                val destFile = File(targetDirectory, tempFile.name)
                val movedSuccess = tempFile.renameTo(destFile) || try {
                    tempFile.copyTo(destFile, overwrite = true)
                    tempFile.delete()
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Error moving temp file ${tempFile.name} to ${destFile.absolutePath}", e)
                    false
                }

                if (movedSuccess && destFile.exists()) {
                    val cache = BookCache(
                        path = destFile.absolutePath,
                        fingerprint = fingerprintResult.fingerprint,
                        textHash = fingerprintResult.textHash,
                        author = fingerprintResult.author,
                        title = fingerprintResult.title,
                        fileSize = destFile.length(),
                        lastScanned = System.currentTimeMillis(),
                        format = fingerprintResult.format
                    )
                    TempFileResult.Saved(cache)
                } else {
                    Log.e(TAG, "Failed to persist unique temp file: ${tempFile.name}")
                    TempFileResult.Error
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing temp file: ${tempFile.name}", e)
            try { tempFile.delete() } catch (_: Exception) {}
            TempFileResult.Error
        }
    }
}
