package com.nightread.app.scanner

import android.util.Log
import com.nightread.app.data.BookCache
import com.nightread.app.data.BookCacheDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class BookScanner(
    private val bookCacheDao: BookCacheDao,
    private val maxConcurrency: Int = 3,
    private val batchSize: Int = 30
) {

    companion object {
        private const val TAG = "BookScanner"
        private val SUPPORTED_EXTENSIONS = setOf("fb2", "fb2.zip", "fbz", "epub", "mobi", "azw", "azw3")
    }

    private val semaphore = Semaphore(maxConcurrency)

    /**
     * Проверка, поддерживается ли формат файла
     */
    fun isSupportedBook(file: File): Boolean {
        if (!file.isFile || file.length() == 0L) return false
        val lowerName = file.name.lowercase(Locale.ROOT)
        return SUPPORTED_EXTENSIONS.any { ext -> lowerName.endsWith(".$ext") }
    }

    /**
     * Рекурсивный сбор всех файлов электронных книг в указанной директории.
     */
    fun collectBookFiles(directory: File): List<File> {
        val resultList = mutableListOf<File>()
        if (!directory.exists() || !directory.isDirectory) return resultList

        val files = directory.listFiles() ?: return resultList
        for (file in files) {
            if (file.isDirectory) {
                resultList.addAll(collectBookFiles(file))
            } else if (isSupportedBook(file)) {
                resultList.add(file)
            }
        }
        return resultList
    }

    /**
     * Сканирование директории с пакетной обработкой (batching) и ограничением параллелизма.
     * Возвращает итоговую статистику сканирования.
     */
    suspend fun scanDirectory(directory: File): ScanResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Starting scan for directory: ${directory.absolutePath}")

        val filesOnDisk = collectBookFiles(directory)
        Log.d(TAG, "Found ${filesOnDisk.size} e-book files on disk.")

        // 1. Синхронизация с БД: удаление записей для несуществующих файлов
        val dbPaths = bookCacheDao.getAllPaths().toSet()
        val diskPaths = filesOnDisk.map { it.absolutePath }.toSet()
        val removedPaths = dbPaths - diskPaths

        if (removedPaths.isNotEmpty()) {
            Log.d(TAG, "Removing ${removedPaths.size} deleted files from database.")
            removedPaths.chunked(batchSize).forEach { chunk ->
                bookCacheDao.deleteByPaths(chunk)
            }
        }

        // 2. Отбор файлов для обработки (новые или измененные по дате/размеру)
        val filesToProcess = mutableListOf<File>()
        var skippedCount = 0

        for (file in filesOnDisk) {
            val cached = bookCacheDao.getByPath(file.absolutePath)
            if (cached != null && cached.fileSize == file.length() && cached.lastScanned >= file.lastModified()) {
                skippedCount++
            } else {
                filesToProcess.add(file)
            }
        }

        Log.d(TAG, "Files to process: ${filesToProcess.size}, skipped (unchanged): $skippedCount")

        // 3. Пакетная обработка файлов пачками по `batchSize` с параллелизмом не более `maxConcurrency`
        var processedCount = 0

        filesToProcess.chunked(batchSize).forEach { chunk ->
            val batchResults = coroutineScope {
                chunk.map { file ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            processSingleFile(file)
                        }
                    }
                }.awaitAll().filterNotNull()
            }

            // Сохранение результатов текущей пачки в БД одной операцией
            if (batchResults.isNotEmpty()) {
                bookCacheDao.insertAll(batchResults)
                processedCount += batchResults.size
                Log.d(TAG, "Processed and saved batch of ${batchResults.size} files. Total: $processedCount")
            }
        }

        val totalDuration = System.currentTimeMillis() - startTime
        Log.d(TAG, "Scan completed in ${totalDuration}ms. Total on disk: ${filesOnDisk.size}, processed: $processedCount, removed: ${removedPaths.size}")

        ScanResult(
            totalFiles = filesOnDisk.size,
            processedFiles = processedCount,
            skippedFiles = skippedCount,
            removedFiles = removedPaths.size,
            durationMs = totalDuration
        )
    }

    /**
     * Обработка одного файла с созданием объекта BookCache
     */
    private fun processSingleFile(file: File): BookCache? {
        return try {
            val fingerprintResult = BookFingerprintGenerator.generate(file)
            BookCache(
                path = file.absolutePath,
                fingerprint = fingerprintResult.fingerprint,
                textHash = fingerprintResult.textHash,
                author = fingerprintResult.author,
                title = fingerprintResult.title,
                fileSize = fingerprintResult.fileSize,
                lastScanned = System.currentTimeMillis(),
                format = fingerprintResult.format
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process file ${file.name}", e)
            null
        }
    }

    data class ScanResult(
        val totalFiles: Int,
        val processedFiles: Int,
        val skippedFiles: Int,
        val removedFiles: Int,
        val durationMs: Long
    )
}
