package com.nightread.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import android.util.Log
import com.nightread.app.service.NewCoverExtractor
import com.nightread.app.service.NewFb2Parser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Менеджер синхронизации с Яндекс Диском.
 * Отвечает за:
 * 1. Получение статистики синхронизации (toDownload, toUpload) с дедупликацией по SHA-1 и имени.
 * 2. Пофайловое кэширование хэшей (SHA-1) файлов Яндекс Диска в локальной БД (CloudFileEntity / CloudFileDao).
 * 3. Загрузку новых книг на диск с сохранением ОРИГИНАЛЬНОГО имени файла (пропуск при совпадении имени или SHA-1).
 * 4. Скачивание новых книг с сохранением оригинальных названий файлов в папку Books.
 * 5. Синхронизацию прогресса чтения.
 */
class YandexSyncManager(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val repository = BookRepository(database.bookDao(), database.noteDao())
    private val cloudFileDao = database.cloudFileDao()

    companion object {
        private const val TAG = "YandexSyncManager"
    }

    /**
     * Проверяет наличие подключения к интернету.
     */
    fun hasInternetConnection(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        return networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    /**
     * Возвращает локальную папку для сохранения книг.
     * Приоритет отдается выбранной пользователем папке через SAF.
     * Если она недоступна, используется общедоступная папка '/storage/emulated/0/Books'.
     * Если запись невозможна или ограничена ОС, выполняется откат на безопасную папку приложения.
     */
    fun getLocalBooksDirectory(): File {
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
                Log.w(TAG, "Cannot write directly to custom folder: ${e.message}. Using default fallback.")
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
            Log.w(TAG, "Cannot write directly to public Books folder: ${e.message}. Using safe fallback.")
        }

        // Откат 1: Внешняя директория приложения
        val extFilesDir = context.getExternalFilesDir("Books")
        if (extFilesDir != null && (extFilesDir.exists() || extFilesDir.mkdirs())) {
            return extFilesDir
        }

        // Откат 2: Внутренний кэш приложения
        val internalBooksDir = File(context.filesDir, "books")
        if (!internalBooksDir.exists()) {
            internalBooksDir.mkdirs()
        }
        return internalBooksDir
    }

    /**
     * Ожидание подключения к интернету, если связь пропала во время работы.
     */
    private suspend fun ensureInternet(
        completed: Int,
        total: Int,
        stage: YandexSyncState.Stage,
        downloadedCount: Int,
        uploadedCount: Int,
        onProgress: (status: String, completed: Int, total: Int, stage: YandexSyncState.Stage, downloaded: Int, uploaded: Int, remainingSeconds: Long) -> Unit
    ) {
        while (!hasInternetConnection()) {
            currentCoroutineContext().ensureActive()
            onProgress(
                "Связь прервана. Ожидание подключения к сети...",
                completed,
                total,
                stage,
                downloadedCount,
                uploadedCount,
                -1L
            )
            delay(3000)
        }
    }

    /**
     * Расчет статистики синхронизации. Сканирует файлы на Яндекс Диске,
     * сопоставляет их с локальной БД, считает SHA-1 (с использованием кэша CloudFileDao)
     * и определяет, какие книги скачать, а какие — загрузить.
     */
    suspend fun calculateSyncStats(onProgress: (status: String) -> Unit): SyncStats? = withContext(Dispatchers.IO) {
        val originalFolder = YandexDiskManager.getSyncFolder(context)
        val token = YandexDiskManager.getToken(context) ?: return@withContext null
        val authHeader = "OAuth $token"

        try {
            onProgress("Поиск папки синхронизации на Яндекс Диске...")
            val syncFolder = YandexDiskManager.resolveCaseInsensitivePath(context, originalFolder)
            if (syncFolder != originalFolder) {
                YandexDiskManager.setSyncFolder(context, syncFolder)
            }

            Log.d(TAG, "calculateSyncStats: Папка синхронизации: $syncFolder")
            onProgress("Проверка директорий на диске...")
            val progressFolder = "$syncFolder/Progress"
            val pathsToCreate = listOf(syncFolder, progressFolder)
            for (path in pathsToCreate) {
                try {
                    YandexDiskManager.api.createDirectory(authHeader, path)
                } catch (e: Exception) {
                    Log.d(TAG, "Папка уже существует: $path")
                }
            }

            onProgress("Получение списка файлов из облака...")
            val cloudItems = YandexDiskManager.getAllFilesFromFolder(context, authHeader, syncFolder)
            
            // Фильтруем только поддерживаемые форматы книг
            val cloudBooks = cloudItems.filter {
                val name = it.name.lowercase()
                name.endsWith(".fb2") || name.endsWith(".fb2.zip") || name.endsWith(".epub")
            }
            val booksOnDisk = cloudBooks.size
            Log.d(TAG, "Найдено книг на диске: $booksOnDisk")

            onProgress("Анализ кэша SHA-1 файлов...")
            val updatedCloudBooks = mutableListOf<CloudFileEntity>()
            val needsSha1 = mutableListOf<ResourceItem>()

            // Проверка кэша в локальной Room-БД
            for (cloudBook in cloudBooks) {
                val cleanPath = YandexDiskManager.normalizePath(cloudBook.path ?: "$syncFolder/${cloudBook.name}")
                val cached = cloudFileDao.getByPath(cleanPath)
                if (cached != null && cached.size == (cloudBook.size ?: 0L) && cached.lastModified == (cloudBook.modified ?: "")) {
                    updatedCloudBooks.add(cached)
                } else {
                    needsSha1.add(cloudBook)
                }
            }

            Log.d(TAG, "Хитов в кэше: ${updatedCloudBooks.size}, требуется рассчитать SHA-1: ${needsSha1.size}")

            // Если есть новые или изменившиеся файлы на диске — вычисляем их хэши в фоне
            if (needsSha1.isNotEmpty()) {
                var processedCount = 0
                val totalToProcess = needsSha1.size
                val startTime = System.currentTimeMillis()
                
                // Concurrency limit of 15 using Semaphore to prevent API throttling
                val semaphore = kotlinx.coroutines.sync.Semaphore(15)
                
                coroutineScope {
                    val deferreds = needsSha1.map { item ->
                        async {
                            semaphore.withPermit {
                                try {
                                    val cleanItemPath = YandexDiskManager.normalizePath(item.path ?: "$syncFolder/${item.name}")
                                    val linkResponse = YandexDiskManager.api.getDownloadLink(authHeader, cleanItemPath)
                                    val responseBody = YandexDiskManager.api.downloadFile(linkResponse.href)
                                    
                                    // Use highly unique temp files for safety in parallel downloads
                                    val tempFile = File(context.cacheDir, "temp_stat_${System.nanoTime()}_${item.name}")
                                    try {
                                        tempFile.outputStream().use { output ->
                                            responseBody.byteStream().use { input ->
                                                input.copyTo(output)
                                            }
                                        }
                                        val bytes = tempFile.readBytes()
                                        val fb2Bytes = extractFb2Bytes(bytes, item.name)
                                        if (fb2Bytes.isNotEmpty()) {
                                            val sha1 = computeSha1(fb2Bytes)
                                            
                                            val entity = CloudFileEntity(
                                                path = cleanItemPath,
                                                sha1 = sha1,
                                                size = item.size ?: 0L,
                                                lastModified = item.modified ?: ""
                                            )
                                            cloudFileDao.insert(entity)
                                            synchronized(updatedCloudBooks) {
                                                updatedCloudBooks.add(entity)
                                            }
                                        } else {
                                            Log.e(TAG, "Empty or missing FB2 in cloud file: ${item.name}")
                                        }
                                    } finally {
                                        if (tempFile.exists()) tempFile.delete()
                                    }
                                } catch (e: retrofit2.HttpException) {
                                    if (e.code() == 401) {
                                        YandexDiskManager.clearToken(context)
                                        Log.e(TAG, "Token expired during SHA1 calculation. Clearing token.")
                                    }
                                    Log.e(TAG, "HTTP Error calculating SHA1 for cloud file: ${item.name}", e)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error calculating SHA1 for cloud file: ${item.name}", e)
                                } finally {
                                    synchronized(this@YandexSyncManager) {
                                        processedCount++
                                        val elapsed = System.currentTimeMillis() - startTime
                                        val avgTime = if (processedCount > 0) elapsed / processedCount else 1L
                                        val remaining = totalToProcess - processedCount
                                        val remainingSecs = (remaining * avgTime) / 1000
                                        val timeStr = if (remainingSecs > 60) "${remainingSecs / 60} мин ${remainingSecs % 60} сек" else "$remainingSecs сек"
                                        onProgress("Индексация облака: $processedCount из $totalToProcess (осталось ~ $timeStr)")
                                    }
                                }
                            }
                        }
                    }
                    deferreds.awaitAll()
                }
            }

            // Получаем список всех локальных книг из базы данных
            val localBooks = database.bookDao().getAllBooks().first()
            val localSha1Set = localBooks.filter { !it.sha1.isNullOrEmpty() }.map { it.sha1 }.toSet()
            
            Log.d(TAG, "Локальных книг (по SHA-1): ${localSha1Set.size}")
            Log.d(TAG, "Книг в облаке (по SHA-1): ${updatedCloudBooks.size}")

            // 1. Книги для скачивания (есть в облаке, но нет локально по SHA-1)
            val toDownload = updatedCloudBooks.filter { !localSha1Set.contains(it.sha1) }
            Log.d(TAG, "Книг для скачивания (разница cloud - local): ${toDownload.size}")

            // 2. Книги для загрузки (есть на устройстве, но нет в облаке по SHA-1)
            val cloudSha1Set = updatedCloudBooks.map { it.sha1 }.toSet()
            
            val toUpload = localBooks.filter { localBook ->
                if (localBook.sha1.isNullOrEmpty()) {
                    Log.w(TAG, "Пропущена книга '${localBook.title}' для загрузки: отсутствует SHA-1")
                    return@filter false
                }
                
                val hasSha1InCloud = cloudSha1Set.contains(localBook.sha1)
                if (hasSha1InCloud) {
                    Log.d(TAG, "Книга '${localBook.title}' пропущена для загрузки: SHA-1 уже есть в облаке")
                }
                
                !hasSha1InCloud
            }
            Log.d(TAG, "Книг для загрузки (разница local - cloud): ${toUpload.size}")

            val duplicates = updatedCloudBooks.size - toDownload.size

            onProgress("Найдено ${toDownload.size} новых книг на диске, ${toUpload.size} книг нужно загрузить на диск")

            val cloudProgressItems = YandexDiskManager.getAllFilesFromFolder(context, authHeader, "$syncFolder/Progress")

            return@withContext SyncStats(
                booksOnDisk = booksOnDisk,
                booksLocal = localBooks.size,
                toDownload = toDownload,
                toUpload = toUpload,
                duplicates = duplicates,
                cloudProgressItems = cloudProgressItems
            )
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 401) {
                YandexDiskManager.clearToken(context)
                Log.e(TAG, "Token expired during calculateSyncStats. Clearing token.")
            }
            Log.e(TAG, "HTTP error in calculateSyncStats", e)
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error in calculateSyncStats", e)
            return@withContext null
        }
    }

    /**
     * Выполняет синхронизацию (скачивание, загрузка, синхронизация прогресса).
     */
    suspend fun performSync(
        stats: SyncStats,
        onProgress: (status: String, completed: Int, total: Int, stage: YandexSyncState.Stage, downloaded: Int, uploaded: Int, remainingSeconds: Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val syncFolder = YandexDiskManager.getSyncFolder(context)
        val token = YandexDiskManager.getToken(context) ?: return@withContext false
        val authHeader = "OAuth $token"
        
        val progressAdapter = YandexDiskManager.moshi.adapter(BookProgressPayload::class.java)

        val totalDownloads = stats.toDownload.size
        val totalUploads = stats.toUpload.size
        val totalTasks = totalDownloads + totalUploads + 1
        var completedTasks = 0

        var downloadedCount = 0
        var uploadedCount = 0

        try {
            // ==========================================
            // 1. СКАЧИВАНИЕ КНИГ (DOWNLOAD)
            // ==========================================
            val downloadStartTime = System.currentTimeMillis()
            val booksDirectory = getLocalBooksDirectory()
            Log.d(TAG, "Локальная папка для скачивания книг: ${booksDirectory.absolutePath}")

            for ((index, cloudItem) in stats.toDownload.withIndex()) {
                currentCoroutineContext().ensureActive()
                ensureInternet(completedTasks, totalTasks, YandexSyncState.Stage.DOWNLOADING, downloadedCount, uploadedCount, onProgress)

                val elapsed = System.currentTimeMillis() - downloadStartTime
                val avgTimePerFile = if (index > 0) elapsed / index else 0L
                val remainingFiles = totalDownloads - index
                val remainingSeconds = if (avgTimePerFile > 0) (remainingFiles * avgTimePerFile) / 1000 else -1L

                val originalName = File(cloudItem.path).name
                onProgress(
                    "Скачивание: $originalName (${index + 1} из $totalDownloads)",
                    completedTasks,
                    totalTasks,
                    YandexSyncState.Stage.DOWNLOADING,
                    downloadedCount,
                    uploadedCount,
                    remainingSeconds
                )

                try {
                    val linkResponse = YandexDiskManager.api.getDownloadLink(authHeader, cloudItem.path)
                    val responseBody = YandexDiskManager.api.downloadFile(linkResponse.href)
                    
                    val tempFile = File(context.cacheDir, "temp_down_${originalName}")
                    try {
                        tempFile.outputStream().use { output ->
                            responseBody.byteStream().use { input ->
                                input.copyTo(output)
                            }
                        }
                        
                        val bytes = tempFile.readBytes()
                        val fb2Bytes = extractFb2Bytes(bytes, originalName)
                        if (fb2Bytes.isNotEmpty()) {
                            // Compute exact SHA-1 on the extracted fb2 content bytes
                            val sha1 = computeSha1(fb2Bytes)
                            
                            // Decode fb2Bytes safely to String respecting XML and Russian Windows-1251 encoding
                            val content = decodeBytesToString(fb2Bytes)
                            
                            // Parse FB2 correctly to get metadata
                            val meta = NewFb2Parser.parse(content, originalName)
                            
                            val coverPath = NewCoverExtractor.extractAndSaveCover(content, sha1, context)
                            
                            // Truncate annotation
                            val truncatedAnnotation = meta.annotation?.take(500)
                            
                            val localFile = File(booksDirectory, originalName)
                            tempFile.copyTo(localFile, overwrite = true)
                            
                            val newBook = BookEntity(
                                sha1 = sha1,
                                title = meta.title,
                                author = meta.author,
                                category = "Локальные",
                                totalCharacters = content.length,
                                coverGradientStart = getRandomGradientStartColor(),
                                coverGradientEnd = getRandomGradientEndColor(),
                                filePath = localFile.absolutePath,
                                series = meta.series,
                                seriesIndex = meta.seriesIndex,
                                language = meta.language,
                                annotation = truncatedAnnotation,
                                fileSize = bytes.size.toLong(),
                                coverPath = coverPath
                            )
                            if (repository.insertBookSafely(newBook)) {
                                downloadedCount++
                                Log.d(TAG, "Успешно скачана и импортирована книга: $originalName (SHA-1: $sha1)")
                            } else {
                                Log.e(TAG, "Ошибка вставки книги '$originalName' в базу")
                            }
                        } else {
                            Log.e(TAG, "Empty or missing FB2 content inside downloaded file: $originalName")
                        }
                    } finally {
                        if (tempFile.exists()) tempFile.delete()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка скачивания книги '$originalName'", e)
                }
                completedTasks++
            }

            // ==========================================
            // 2. ЗАГРУЗКА КНИГ (UPLOAD)
            // ==========================================
            val uploadStartTime = System.currentTimeMillis()
            for ((index, localBook) in stats.toUpload.withIndex()) {
                currentCoroutineContext().ensureActive()
                ensureInternet(completedTasks, totalTasks, YandexSyncState.Stage.UPLOADING, downloadedCount, uploadedCount, onProgress)

                val elapsed = System.currentTimeMillis() - uploadStartTime
                val avgTimePerFile = if (index > 0) elapsed / index else 0L
                val remainingFiles = totalUploads - index
                val remainingSeconds = if (avgTimePerFile > 0) (remainingFiles * avgTimePerFile) / 1000 else -1L

                val localFile = localBook.filePath?.let { File(it) }
                val originalName = localFile?.name ?: "${localBook.title}.fb2"

                onProgress(
                    "Загрузка: $originalName (${index + 1} из $totalUploads)",
                    completedTasks,
                    totalTasks,
                    YandexSyncState.Stage.UPLOADING,
                    downloadedCount,
                    uploadedCount,
                    remainingSeconds
                )

                if (localFile != null && localFile.exists()) {
                    try {
                        val cleanPath = YandexDiskManager.normalizePath("$syncFolder/$originalName")
                        val linkResponse = YandexDiskManager.api.getUploadLink(authHeader, cleanPath)
                        val fileBytes = localFile.readBytes()
                        YandexDiskManager.api.uploadFile(
                            linkResponse.href,
                            fileBytes.toRequestBody("application/octet-stream".toMediaType())
                        )
                        
                        // Сохраняем в кэш
                        val entity = CloudFileEntity(
                            path = cleanPath,
                            sha1 = localBook.sha1,
                            size = fileBytes.size.toLong(),
                            lastModified = "" // Обновится при следующем сканировании
                        )
                        cloudFileDao.insert(entity)
                        uploadedCount++
                        Log.d(TAG, "Успешно загружена книга: $originalName")
                    } catch (e: Exception) {
                        Log.e(TAG, "Ошибка загрузки книги '$originalName'", e)
                    }
                } else {
                    Log.w(TAG, "Файл книги не найден на устройстве для загрузки: ${localBook.title}")
                }
                completedTasks++
            }

            // ==========================================
            // 3. СИНХРОНИЗАЦИЯ ПРОГРЕССА ЧТЕНИЯ
            // ==========================================
            currentCoroutineContext().ensureActive()
            ensureInternet(completedTasks, totalTasks, YandexSyncState.Stage.PROGRESS_SYNC, downloadedCount, uploadedCount, onProgress)

            onProgress(
                "Синхронизация прогресса чтения...",
                completedTasks,
                totalTasks,
                YandexSyncState.Stage.PROGRESS_SYNC,
                downloadedCount,
                uploadedCount,
                -1L
            )

            // Скачиваем прогресс с облака
            for (progressItem in stats.cloudProgressItems) {
                currentCoroutineContext().ensureActive()
                if (progressItem.name.endsWith(".json")) {
                    try {
                        val cleanPath = YandexDiskManager.normalizePath(progressItem.path ?: "$syncFolder/Progress/${progressItem.name}")
                        val linkResponse = YandexDiskManager.api.getDownloadLink(authHeader, cleanPath)
                        val body = YandexDiskManager.api.downloadFile(linkResponse.href)
                        val jsonStr = body.string()
                        val cloudProgress = progressAdapter.fromJson(jsonStr)
                        if (cloudProgress != null) {
                            val localBook = repository.getBookBySha1(cloudProgress.sha1)
                            if (localBook != null) {
                                if (cloudProgress.lastReadTime > localBook.lastReadTime) {
                                    repository.updateBook(localBook.copy(
                                        currentProgressChar = cloudProgress.currentProgressChar,
                                        lastReadTime = cloudProgress.lastReadTime
                                    ))
                                    Log.d(TAG, "Обновлен локальный прогресс для книги: ${localBook.title}")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Ошибка синхронизации прогресса: ${progressItem.name}", e)
                    }
                }
            }

            // Загружаем наш локальный прогресс
            val updatedLocalBooks = repository.allBooks.first()
            for (localBook in updatedLocalBooks) {
                currentCoroutineContext().ensureActive()
                val cloudProgressName = "progress_${localBook.sha1}.json"
                val matchingCloudProgress = stats.cloudProgressItems.find { it.name == cloudProgressName }
                val shouldUploadProgress = (matchingCloudProgress == null && localBook.currentProgressChar > 0) || matchingCloudProgress != null

                if (shouldUploadProgress) {
                    try {
                        val payload = BookProgressPayload(
                            sha1 = localBook.sha1,
                            title = localBook.title,
                            currentProgressChar = localBook.currentProgressChar,
                            lastReadTime = localBook.lastReadTime
                        )
                        val json = progressAdapter.toJson(payload)
                        val cleanPath = YandexDiskManager.normalizePath("$syncFolder/Progress/$cloudProgressName")
                        val link = YandexDiskManager.api.getUploadLink(authHeader, cleanPath)
                        YandexDiskManager.api.uploadFile(
                            link.href,
                            json.toByteArray(StandardCharsets.UTF_8).toRequestBody("application/json".toMediaType())
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Ошибка отправки прогресса для '${localBook.title}'", e)
                    }
                }
            }

            completedTasks++
            YandexDiskManager.saveSyncTimestamp(context)
            
            onProgress(
                "Синхронизация успешно завершена!",
                completedTasks,
                totalTasks,
                YandexSyncState.Stage.COMPLETED,
                downloadedCount,
                uploadedCount,
                0L
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Критическая ошибка при выполнении performSync", e)
            false
        }
    }

    private fun computeSha1(bytes: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-1")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun extractFb2Bytes(bytes: ByteArray, fileName: String): ByteArray {
        return try {
            val lowerName = fileName.lowercase()
            if (lowerName.endsWith(".zip") || lowerName.endsWith(".fb2.zip")) {
                java.util.zip.ZipInputStream(bytes.inputStream()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory && entry.name.lowercase().endsWith(".fb2")) {
                            return zis.readBytes()
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
    
    private fun extractContentFromBytes(bytes: ByteArray, fileName: String): String {
        return try {
            if (fileName.lowercase().endsWith(".zip")) {
                java.util.zip.ZipInputStream(bytes.inputStream()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory && entry.name.lowercase().endsWith(".fb2")) {
                            return zis.bufferedReader().use { it.readText() }
                        }
                        entry = zis.nextEntry
                    }
                }
                ""
            } else {
                String(bytes, StandardCharsets.UTF_8)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting content from $fileName", e)
            ""
        }
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
