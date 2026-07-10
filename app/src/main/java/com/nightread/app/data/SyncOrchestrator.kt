package com.nightread.app.data

import android.content.Context
import android.os.Environment
import android.util.Log
import com.nightread.app.service.NewCoverExtractor
import com.nightread.app.service.NewFb2Parser
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Оркестратор синхронизации с Яндекс Диском.
 * Отвечает за координацию шагов: получение списка файлов, сопоставление хэшей, скачивание и загрузку книг.
 */
class SyncOrchestrator(
    private val context: Context,
    private val cloudService: CloudFileService,
    private val sha1Extractor: Sha1Extractor,
    private val cacheManager: SyncCacheManager,
    private val progressTracker: SyncProgressTracker
) {
    private val TAG = "SYNC_ORCHESTRATOR"
    
    var isCancelled: Boolean
        get() = SyncCancellationManager.isCancelled()
        set(value) { SyncCancellationManager.setCancelled(value) }

    suspend fun sync() {
        SyncCancellationManager.setCancelled(false)
        val syncFolder = try {
            YandexDiskManager.getSyncFolder(context)
        } catch (e: Exception) {
            SyncErrorHandler.logError("SyncOrchestrator", e, false)
            throw Exception("Не удалось получить путь папки синхронизации: ${e.localizedMessage}", e)
        }
        
        Log.d(TAG, "Starting sync in folder: $syncFolder")

        val syncManager = YandexSyncManager(context)
        if (!syncManager.hasInternetConnection()) {
            SyncErrorHandler.logError("SyncOrchestrator Internet", Exception("No internet connection during synchronization initialization."), false)
            throw Exception("Отсутствует подключение к интернету")
        }
        
        val token = YandexDiskManager.getToken(context)
        if (token.isNullOrEmpty()) {
            SyncErrorHandler.logError("SyncOrchestrator Token", Exception("Yandex Disk token is empty or null."), false)
            throw Exception("Ошибка авторизации Яндекс Диска: отсутствует токен")
        }

        try {
            // Stage 1: Получение списка файлов с диска
            progressTracker.startStage("Получение списка файлов", 0, "Получение списка файлов с диска...")
            if (isCancelled) return
            val cloudFiles = try {
                cloudService.getFileList(syncFolder)
            } catch (e: Exception) {
                Log.e(TAG, "Stage 1: Failed to retrieve file list from $syncFolder", e)
                throw Exception("Не удалось получить список файлов с Яндекс Диска: ${e.localizedMessage}", e)
            }
            Log.d(TAG, "Found ${cloudFiles.size} files in $syncFolder")

            // Filter for supported formats: .fb2, .fb2.zip, .zip and .epub
            val filteredCloudFiles = cloudFiles.filter {
                val name = it.name.lowercase()
                name.endsWith(".fb2") || name.endsWith(".fb2.zip") || name.endsWith(".zip") || name.endsWith(".epub")
            }
            Log.d(TAG, "Filtered ${filteredCloudFiles.size} books to process")

            if (filteredCloudFiles.isEmpty()) {
                progressTracker.finishStage("Сравнение с библиотекой", "Книг на диске не найдено.")
            }

            // Stage 2: Анализ файлов с диска (вычисление или получение SHA-1 из кэша)
            progressTracker.startStage("Вычисление SHA-1", filteredCloudFiles.size, "Анализ файлов на диске...")
            val cloudSha1Map = java.util.concurrent.ConcurrentHashMap<String, String>() // cloudPath -> sha1
            val cloudSha1ToPath = java.util.concurrent.ConcurrentHashMap<String, String>() // sha1 -> cloudPath
            
            val processedCount = java.util.concurrent.atomic.AtomicInteger(0)
            val semaphore = Semaphore(5)

            coroutineScope {
                val jobs = filteredCloudFiles.map { file ->
                    async {
                        semaphore.withPermit {
                            if (isCancelled) return@async
                            
                            val normalizedPath = YandexDiskManager.normalizePath(file.path)

                            try {
                                val cached = cacheManager.getByPath(normalizedPath)
                                var sha1: String? = null

                                if (cached != null && cached.lastModified == file.modified && cached.size == (file.size ?: 0L)) {
                                    sha1 = cached.sha1
                                    Log.d(TAG, "Reusing cached SHA-1 for ${file.name}: $sha1")
                                } else {
                                    Log.d(TAG, "Downloading temporarily and extracting SHA-1 for ${file.name}")
                                    val tempFile = File(context.cacheDir, "temp_sha_${System.currentTimeMillis()}_${java.util.UUID.randomUUID()}_${file.name}")
                                    try {
                                        val success = cloudService.downloadFile(normalizedPath, tempFile)
                                        if (success) {
                                            sha1 = sha1Extractor.extractSha1(tempFile)
                                            if (sha1 != null) {
                                                cacheManager.save(sha1, normalizedPath, file.modified ?: "", file.size ?: 0L)
                                                Log.d(TAG, "Calculated and cached SHA-1 for ${file.name}: $sha1")
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error calculating SHA-1 for ${file.name}", e)
                                    } finally {
                                        try {
                                            if (tempFile.exists()) {
                                                tempFile.delete()
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Failed to delete temp SHA-1 file: ${tempFile.absolutePath}", e)
                                        }
                                    }
                                }

                                if (sha1 != null) {
                                    cloudSha1Map[normalizedPath] = sha1
                                    cloudSha1ToPath[sha1] = normalizedPath
                                }
                            } catch (e: Exception) {
                                SyncErrorHandler.logError("SyncOrchestrator Stage 2", e, true)
                            } finally {
                                val processed = processedCount.incrementAndGet()
                                synchronized(progressTracker) {
                                    progressTracker.updateProgress(processed, filteredCloudFiles.size, "Анализ файлов: $processed из ${filteredCloudFiles.size}")
                                }
                            }
                        }
                    }
                }
                jobs.awaitAll()
            }

            // Stage 3: Получение локальных SHA-1 и сравнение
            progressTracker.startStage("Сравнение с библиотекой", 0, "Сравнение с библиотекой...")
            if (isCancelled) return
            val db = AppDatabase.getDatabase(context)
            val bookDao = db.bookDao()
            val repository = BookRepository(bookDao, db.noteDao())
            
            val localSha1s = try {
                bookDao.getAllSha1s().toSet()
            } catch (e: Exception) {
                Log.e(TAG, "Stage 3: Failed to retrieve local SHA-1 set from database", e)
                throw Exception("Ошибка чтения локальной библиотеки при сопоставлении: ${e.localizedMessage}", e)
            }
            val cloudSha1s = cloudSha1Map.values.toSet()

            val toUploadSha1s = localSha1s - cloudSha1s
            val toDownloadSha1s = cloudSha1s - localSha1s

            val toUploadBooks = mutableListOf<BookEntity>()
            for (sha1 in toUploadSha1s) {
                try {
                    val book = bookDao.getBookBySha1(sha1)
                    if (book != null) {
                        toUploadBooks.add(book)
                        if (toUploadBooks.size <= 10) {
                            Log.d(TAG, "Problematic book to upload (SHA-1): $sha1, title: ${book.title}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error looking up local book by SHA-1: $sha1", e)
                }
            }

            val toDownloadPaths = toDownloadSha1s.mapNotNull { cloudSha1ToPath[it] }
            
            toDownloadSha1s.take(10).forEach { sha1 ->
                Log.d(TAG, "Problematic book to download (SHA-1): $sha1, path: ${cloudSha1ToPath[sha1]}")
            }

            progressTracker.updateStats(
                toUpload = toUploadBooks.size,
                toDownload = toDownloadPaths.size,
                uploaded = 0,
                downloaded = 0
            )

            Log.d(TAG, "To upload: ${toUploadBooks.size}, To download: ${toDownloadPaths.size}")

            // Stage 4: Загрузка на диск (Upload)
            val uploadedCount = java.util.concurrent.atomic.AtomicInteger(0)
            val uploadProcessedCount = java.util.concurrent.atomic.AtomicInteger(0)
            if (toUploadBooks.isNotEmpty()) {
                progressTracker.startStage("Загрузка на диск", toUploadBooks.size, "Загрузка книг на Яндекс Диск...")
                val semaphore = Semaphore(5)
                coroutineScope {
                    val jobs = toUploadBooks.map { book ->
                        async {
                            semaphore.withPermit {
                                if (isCancelled) return@async
                                try {
                                    val localFile = book.filePath?.let { File(it) }
                                    val originalName = localFile?.name ?: "${book.title}.fb2"
                                    if (localFile != null && localFile.exists()) {
                                        val remotePath = YandexDiskManager.normalizePath("$syncFolder/$originalName")
                                        
                                        YandexSyncState.update {
                                            it.copy(
                                                currentFileName = originalName,
                                                currentFileBytesTransferred = 0L,
                                                currentFileTotalBytes = localFile.length()
                                            )
                                        }

                                        val success = cloudService.uploadFile(localFile, remotePath)
                                        if (success) {
                                            uploadedCount.incrementAndGet()
                                            // Cache the SHA-1 for the uploaded file by querying Yandex Disk's metadata
                                            try {
                                                val tokenVal = YandexDiskManager.getToken(context)
                                                if (tokenVal != null) {
                                                    val response = YandexDiskManager.api.getResource("OAuth $tokenVal", remotePath, limit = 1)
                                                    val modified = response.modified ?: ""
                                                    cacheManager.save(book.sha1 ?: "", remotePath, modified, response.size ?: localFile.length())
                                                }
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Error caching metadata after upload", e)
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    SyncErrorHandler.logError("SyncOrchestrator Upload", e, true)
                                } finally {
                                    val processed = uploadProcessedCount.incrementAndGet()
                                    val currentUploaded = uploadedCount.get()
                                    synchronized(progressTracker) {
                                        progressTracker.updateProgress(processed, toUploadBooks.size, "Загрузка на диск: $processed из ${toUploadBooks.size}")
                                        progressTracker.updateStats(
                                            toUpload = toUploadBooks.size,
                                            toDownload = toDownloadPaths.size,
                                            uploaded = currentUploaded,
                                            downloaded = 0
                                        )
                                    }
                                }
                            }
                        }
                    }
                    jobs.awaitAll()
                }
            }

            // Clean file progress status in YandexSyncState
            YandexSyncState.update {
                it.copy(
                    currentFileName = null,
                    currentFileBytesTransferred = 0L,
                    currentFileTotalBytes = 0L
                )
            }

            // Stage 5: Скачивание с диска (Download)
            val downloadedCount = java.util.concurrent.atomic.AtomicInteger(0)
            val downloadProcessedCount = java.util.concurrent.atomic.AtomicInteger(0)
            val booksDirectory = getLocalBooksDirectory()
            Log.d(TAG, "Local download directory: ${booksDirectory.absolutePath}")

            if (toDownloadPaths.isNotEmpty()) {
                progressTracker.startStage("Скачивание с диска", toDownloadPaths.size, "Скачивание новых книг...")
                val semaphore = Semaphore(5)
                coroutineScope {
                    val jobs = toDownloadPaths.map { remotePath ->
                        async {
                            semaphore.withPermit {
                                if (isCancelled) return@async

                                val originalName = File(remotePath).name
                                val tempFile = File(context.cacheDir, "temp_down_${System.currentTimeMillis()}_${java.util.UUID.randomUUID()}_$originalName")
                                try {
                                    val success = cloudService.downloadFile(remotePath, tempFile)
                                    if (success && tempFile.exists()) {
                                        val bytes = tempFile.readBytes()
                                        val fb2Bytes = extractFb2Bytes(bytes, originalName)
                                        if (fb2Bytes.isNotEmpty()) {
                                            val sha1 = computeSha1(fb2Bytes)
                                            val content = decodeBytesToString(fb2Bytes)
                                            val meta = NewFb2Parser.parse(content, originalName)
                                            val coverPath = NewCoverExtractor.extractAndSaveCover(content, sha1, context)
                                            val truncatedAnnotation = meta.annotation?.take(500)

                                            val localFile = File(booksDirectory, originalName)
                                            tempFile.copyTo(localFile, overwrite = true)

                                            try {
                                                val newBook = BookEntity(
                                                    sha1 = sha1,
                                                    title = meta.title,
                                                    author = meta.author ?: "Неизвестен",
                                                    category = "Локальные",
                                                    currentProgressChar = 0,
                                                    
                                                    lastReadTime = System.currentTimeMillis(),
                                                    filePath = localFile.absolutePath,
                                                    series = meta.series,
                                                    seriesIndex = meta.seriesIndex,
                                                    language = meta.language ?: "ru",
                                                    fileSize = bytes.size.toLong(),
                                                    review = null,
                                                    isFavorite = false,
                                                    coverPath = coverPath,
                                                    annotation = truncatedAnnotation,
                                                    currentPageIndex = 0,
                                                    coverGradientStart = getRandomGradientStartColor(),
                                                    coverGradientEnd = getRandomGradientEndColor()
                                                )

                                                val inserted = bookDao.insertBookSafely(newBook)

                                                if (inserted) {
                                                    Log.d(TAG, "Successfully inserted book: ${meta.title} ($sha1)")
                                                    downloadedCount.incrementAndGet()
                                                    // Save to cache
                                                    try {
                                                        val tokenVal = YandexDiskManager.getToken(context)
                                                        if (tokenVal != null) {
                                                            val response = YandexDiskManager.api.getResource("OAuth $tokenVal", remotePath, limit = 1)
                                                            cacheManager.save(sha1, remotePath, response.modified ?: "", response.size ?: bytes.size.toLong())
                                                        }
                                                    } catch (e: Exception) {
                                                        Log.e(TAG, "Error caching after download", e)
                                                    }
                                                } else {
                                                    Log.e(TAG, "Failed to insert book: ${meta.title} ($sha1)")
                                                }
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Exception creating or inserting BookEntity for ${meta.title}", e)
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    SyncErrorHandler.logError("SyncOrchestrator Download", e, true)
                                } finally {
                                    try {
                                        if (tempFile.exists()) {
                                            tempFile.delete()
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Failed to delete temp downloaded file: ${tempFile.absolutePath}", e)
                                    }
                                    val processed = downloadProcessedCount.incrementAndGet()
                                    val currentDownloaded = downloadedCount.get()
                                    val currentUploaded = uploadedCount.get()
                                    synchronized(progressTracker) {
                                        progressTracker.updateProgress(processed, toDownloadPaths.size, "Скачивание с диска: $processed из ${toDownloadPaths.size}")
                                        progressTracker.updateStats(
                                            toUpload = toUploadBooks.size,
                                            toDownload = toDownloadPaths.size,
                                            uploaded = currentUploaded,
                                            downloaded = currentDownloaded
                                        )
                                    }
                                }
                            }
                        }
                    }
                    jobs.awaitAll()
                }
            }

            if (isCancelled) {
                progressTracker.finishStage("Отмена", "Синхронизация отменена пользователем")
                progressTracker.showFinalNotification(
                    "Синхронизация отменена",
                    "Операция была отменена пользователем.",
                    false
                )
                YandexSyncState.update {
                    it.copy(
                        isRunning = false,
                        stage = YandexSyncState.Stage.IDLE,
                        statusText = "Синхронизация отменена",
                        finished = true,
                        success = false,
                        error = "Синхронизация отменена"
                    )
                }
                return
            }

            // Finish
            val finishMsg = "Синхронизация завершена! Загружено: ${uploadedCount.get()}, скачано: ${downloadedCount.get()}"
            progressTracker.finishStage("Завершено", finishMsg)
            progressTracker.showFinalNotification("Синхронизация завершена", finishMsg, true)

            YandexSyncState.update {
                it.copy(
                    isRunning = false,
                    stage = YandexSyncState.Stage.COMPLETED,
                    statusText = finishMsg,
                    finished = true,
                    success = true,
                    downloadedCount = downloadedCount.get(),
                    uploadedCount = uploadedCount.get()
                )
            }
            YandexDiskManager.saveSyncTimestamp(context)

        } catch (e: Exception) {
            Log.e(TAG, "Sync orchestrator exception", e)
            val errorMsg = e.localizedMessage ?: "Неизвестная ошибка"
            progressTracker.finishStage("Ошибка", errorMsg)
            progressTracker.showFinalNotification("Синхронизация прервана", errorMsg, false)

            YandexSyncState.update {
                it.copy(
                    isRunning = false,
                    stage = YandexSyncState.Stage.ERROR,
                    statusText = errorMsg,
                    finished = true,
                    success = false,
                    error = errorMsg
                )
            }
        }
    }

    private fun getLocalBooksDirectory(): File {
        val customUriStr = SyncSettingsManager.getDownloadFolderUri(context)
        if (customUriStr != null) {
            try {
                val uri = android.net.Uri.parse(customUriStr)
                val path = SyncSettingsManager.resolveUriToPath(context, uri)
                if (path != null) {
                    val customDir = File(path)
                    if (!customDir.exists()) {
                        customDir.mkdirs()
                    }
                    if (customDir.exists() && customDir.canWrite()) {
                        return customDir
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cannot write to custom folder: ${e.message}")
            }
        }

        val externalBooksDir = File(Environment.getExternalStorageDirectory(), "Books")
        try {
            if (!externalBooksDir.exists()) {
                externalBooksDir.mkdirs()
            }
            if (externalBooksDir.exists() && externalBooksDir.canWrite()) {
                return externalBooksDir
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot write to public Books folder: ${e.message}")
        }

        val extFilesDir = context.getExternalFilesDir("Books")
        if (extFilesDir != null && (extFilesDir.exists() || extFilesDir.mkdirs())) {
            return extFilesDir
        }

        val internalBooksDir = File(context.filesDir, "books")
        if (!internalBooksDir.exists()) {
            internalBooksDir.mkdirs()
        }
        return internalBooksDir
    }

    private fun extractFb2Bytes(bytes: ByteArray, fileName: String): ByteArray {
        return try {
            val lowerName = fileName.lowercase()
            if (lowerName.endsWith(".zip") || lowerName.endsWith(".fb2.zip")) {
                java.util.zip.ZipInputStream(bytes.inputStream()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory && entry.name.lowercase().endsWith(".fb2")) {
                            val buffer = java.io.ByteArrayOutputStream()
                            val data = ByteArray(8192)
                            var nRead: Int
                            while (zis.read(data, 0, data.size).also { nRead = it } != -1) {
                                buffer.write(data, 0, nRead)
                            }
                            return buffer.toByteArray()
                        }
                        entry = zis.nextEntry
                    }
                }
                byteArrayOf()
            } else {
                bytes
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting FB2 bytes from $fileName", e)
            byteArrayOf()
        }
    }

    private fun decodeBytesToString(bytes: ByteArray): String {
        return try {
            val prefixLen = minOf(bytes.size, 2048)
            val prefix = String(bytes, 0, prefixLen, StandardCharsets.UTF_8)
            if (prefix.contains("<?xml", ignoreCase = true) || prefix.contains("<fictionbook", ignoreCase = true)) {
                String(bytes, StandardCharsets.UTF_8)
            } else {
                String(bytes, java.nio.charset.Charset.forName("windows-1251"))
            }
        } catch (e: Exception) {
            String(bytes, StandardCharsets.UTF_8)
        }
    }

    private fun computeSha1(bytes: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-1")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun getRandomGradientStartColor(): String {
        val colors = listOf("#E0A96D", "#D4A373", "#CCA43B", "#C5A880", "#B5838D", "#E5989B")
        return colors.random()
    }

    private fun getRandomGradientEndColor(): String {
        val colors = listOf("#201A15", "#432818", "#3D348B", "#6F4E37", "#582F0E", "#6A4C93")
        return colors.random()
    }
}
