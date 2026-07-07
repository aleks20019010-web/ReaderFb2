package com.nightread.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.nightread.app.service.NewCoverExtractor
import com.nightread.app.service.NewFb2Parser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Менеджер синхронизации с Яндекс Диском.
 * Управляет проверкой подключения, подсчетом статистики и непосредственным выполнением синхронизации
 * с передачей подробного прогресса и оценкой оставшегося времени.
 */
class YandexSyncManager(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val repository = BookRepository(database.bookDao(), database.noteDao())
    private val cloudFileDao = database.cloudFileDao()

    /**
     * Проверяет наличие подключения к интернету.
     */
    fun hasInternetConnection(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        return networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    /**
     * Приостанавливает выполнение корутины, если отсутствует интернет, и сообщает об этом в callback.
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
                "Отсутствует подключение к интернету. Ожидание сети...",
                completed,
                total,
                stage,
                downloadedCount,
                uploadedCount,
                -1L
            )
            delay(2000)
        }
    }

    /**
     * Запускает расчет статистики для синхронизации.
     */
    suspend fun calculateSyncStats(onProgress: (status: String) -> Unit): SyncStats? = withContext(Dispatchers.IO) {
        YandexDiskManager.calculateSyncStats(context, onProgress)
    }

    /**
     * Выполняет синхронизацию (скачивание, загрузку и синхронизацию прогресса) с подробными отчетами.
     */
    suspend fun performSync(
        stats: SyncStats,
        onProgress: (status: String, completed: Int, total: Int, stage: YandexSyncState.Stage, downloadedCount: Int, uploadedCount: Int, remainingSeconds: Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val syncFolder = YandexDiskManager.getSyncFolder(context)
        val token = YandexDiskManager.getToken(context) ?: return@withContext false
        val authHeader = "OAuth $token"
        
        val api = YandexDiskManager.api
        val moshi = YandexDiskManager.moshi
        val progressAdapter = moshi.adapter(BookProgressPayload::class.java)

        val totalDownloads = stats.toDownload.size
        val totalUploads = stats.toUpload.size
        // Всего задач: скачивание + загрузка + 1 (синхронизация прогресса)
        val totalTasks = totalDownloads + totalUploads + 1
        var completedTasks = 0

        var downloadedCount = 0
        var uploadedCount = 0

        try {
            // ==========================================
            // 1. СКАЧИВАНИЕ КНИГ (DOWNLOAD)
            // ==========================================
            val downloadStartTime = System.currentTimeMillis()
            for ((index, cloudItem) in stats.toDownload.withIndex()) {
                currentCoroutineContext().ensureActive()
                ensureInternet(completedTasks, totalTasks, YandexSyncState.Stage.DOWNLOADING, downloadedCount, uploadedCount, onProgress)

                // Расчет оставшегося времени скачивания
                val elapsed = System.currentTimeMillis() - downloadStartTime
                val avgTimePerFile = if (index > 0) elapsed / index else 0L
                val remainingFiles = totalDownloads - index
                val remainingSeconds = if (avgTimePerFile > 0) (remainingFiles * avgTimePerFile) / 1000 else -1L

                onProgress(
                    "Скачивание: ${index + 1} из $totalDownloads",
                    completedTasks,
                    totalTasks,
                    YandexSyncState.Stage.DOWNLOADING,
                    downloadedCount,
                    uploadedCount,
                    remainingSeconds
                )

                try {
                    val cleanPath = YandexDiskManager.normalizePath("$syncFolder/${cloudItem.name}")
                    val linkResponse = api.getDownloadLink(authHeader, cleanPath)
                    val responseBody = api.downloadFile(linkResponse.href)
                    
                    val tempFile = File(context.cacheDir, "temp_download_${cloudItem.name}")
                    try {
                        tempFile.outputStream().use { output ->
                            responseBody.byteStream().use { input ->
                                input.copyTo(output)
                            }
                        }
                        
                        val bytes = tempFile.readBytes()
                        val content = String(bytes, StandardCharsets.UTF_8)
                        
                        val meta = NewFb2Parser.parse(content, cloudItem.name)
                        val sha1 = cloudItem.sha1
                        
                        val coverPath = NewCoverExtractor.extractAndSaveCover(content, sha1, context)
                        val strippedContent = NewCoverExtractor.stripBinarySections(content)
                        
                        val booksDir = File(context.filesDir, "books")
                        if (!booksDir.exists()) booksDir.mkdirs()
                        val localFile = File(booksDir, cloudItem.name)
                        tempFile.copyTo(localFile, overwrite = true)
                        
                        val newBook = BookEntity(
                            sha1 = sha1,
                            title = meta.title,
                            author = meta.author,
                            content = strippedContent,
                            category = "Локальные",
                            totalCharacters = strippedContent.length,
                            coverGradientStart = getRandomGradientStartColor(),
                            coverGradientEnd = getRandomGradientEndColor(),
                            filePath = localFile.absolutePath,
                            series = meta.series,
                            seriesIndex = meta.seriesIndex,
                            language = meta.language,
                            annotation = meta.annotation,
                            fileSize = bytes.size.toLong(),
                            coverPath = coverPath
                        )
                        repository.insertBook(newBook)
                        downloadedCount++
                    } finally {
                        if (tempFile.exists()) tempFile.delete()
                    }
                } catch (e: Exception) {
                    Log.e("YandexSyncManager", "Error downloading book: ${cloudItem.name}", e)
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

                // Расчет оставшегося времени загрузки
                val elapsed = System.currentTimeMillis() - uploadStartTime
                val avgTimePerFile = if (index > 0) elapsed / index else 0L
                val remainingFiles = totalUploads - index
                val remainingSeconds = if (avgTimePerFile > 0) (remainingFiles * avgTimePerFile) / 1000 else -1L

                onProgress(
                    "Загрузка: ${index + 1} из $totalUploads",
                    completedTasks,
                    totalTasks,
                    YandexSyncState.Stage.UPLOADING,
                    downloadedCount,
                    uploadedCount,
                    remainingSeconds
                )

                val localFile = localBook.filePath?.let { File(it) }
                if (localFile != null && localFile.exists()) {
                    val ext = if (localFile.name.endsWith(".zip")) ".fb2.zip" else ".fb2"
                    val filename = "${localBook.sha1}$ext"
                    try {
                        val cleanPath = YandexDiskManager.normalizePath("$syncFolder/$filename")
                        val linkResponse = api.getUploadLink(authHeader, cleanPath)
                        val fileBytes = localFile.readBytes()
                        api.uploadFile(linkResponse.href, fileBytes.toRequestBody("application/octet-stream".toMediaType()))
                        
                        val cloudCache = CloudFileCache.loadCache(context)
                        cloudCache.entries[filename] = CloudFileEntry(
                            name = filename,
                            size = fileBytes.size.toLong(),
                            modified = "", // unknown until next scan
                            sha1 = localBook.sha1
                        )
                        CloudFileCache.saveCache(context, cloudCache)
                        uploadedCount++
                    } catch (e: Exception) {
                        Log.e("YandexSyncManager", "Error uploading book: $filename", e)
                    }
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
                        val linkResponse = api.getDownloadLink(authHeader, cleanPath)
                        val body = api.downloadFile(linkResponse.href)
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
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("YandexSyncManager", "Error syncing progress file: ${progressItem.name}", e)
                    }
                }
            }

            // Загружаем наш локальный прогресс
            val updatedLocalBooks = repository.allBooks.first()
            for (localBook in updatedLocalBooks) {
                currentCoroutineContext().ensureActive()
                val cloudProgressName = "progress_${localBook.sha1}.json"
                val matchingCloudProgress = stats.cloudProgressItems.find { it.name == cloudProgressName }
                val shouldUploadProgress = matchingCloudProgress == null && localBook.currentProgressChar > 0 || matchingCloudProgress != null

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
                        val link = api.getUploadLink(authHeader, cleanPath)
                        api.uploadFile(link.href, json.toByteArray(StandardCharsets.UTF_8).toRequestBody("application/json".toMediaType()))
                    } catch (e: Exception) {
                        Log.e("YandexSyncManager", "Error pushing progress: ${localBook.title}", e)
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
            Log.e("YandexSyncManager", "Error during performSync", e)
            false
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
