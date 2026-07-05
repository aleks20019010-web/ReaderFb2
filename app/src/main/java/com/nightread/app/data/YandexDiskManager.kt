package com.nightread.app.data

import android.content.Context
import android.util.Log
import com.nightread.app.service.NewCoverExtractor
import com.nightread.app.service.NewFb2Parser
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

object YandexDiskManager {
    suspend fun getFolders(context: Context, path: String = "disk:/"): List<ResourceItem> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val token = getToken(context) ?: return@withContext emptyList()
            val authHeader = "OAuth $token"
            try {
                val response = api.getResource(authHeader, path, limit = 500)
                response.embedded?.items?.filter { it.type == "dir" } ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    fun getSyncFolder(context: Context): String {
        val prefs = context.getSharedPreferences("yandex_sync", Context.MODE_PRIVATE)
        return prefs.getString("sync_folder", "disk:/Books") ?: "disk:/Books"
    }

    fun setSyncFolder(context: Context, folder: String) {
        val prefs = context.getSharedPreferences("yandex_sync", Context.MODE_PRIVATE)
        prefs.edit().putString("sync_folder", folder).apply()
    }

    private const val TAG = "YandexDiskManager"
    private const val PREFS_NAME = "yandex_prefs"
    private const val KEY_TOKEN = "oauth_token"
    private const val BASE_URL = "https://cloud-api.yandex.net/"

    // Placeholder Client ID for Yandex OAuth. Users can replace this with their own.
    const val CLIENT_ID = "bfdea73d1e6242ba826f15d9d0374005"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val api: YandexDiskApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(YandexDiskApi::class.java)
    }

    /**
     * Checks if user has authorized Yandex Disk
     */
    fun isAuthorized(context: Context): Boolean {
        return !getToken(context).isNullOrBlank()
    }

    /**
     * Gets the stored OAuth Token
     */
    fun getToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_TOKEN, null)
    }

    /**
     * Saves the Yandex Disk OAuth Token
     */
    fun saveToken(context: Context, token: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_TOKEN, token).apply()
        Log.d(TAG, "Yandex Disk OAuth Token saved successfully.")
    }

    /**
     * Clears Yandex Disk authorization
     */
    fun clearToken(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_TOKEN).apply()
        Log.d(TAG, "Yandex Disk OAuth Token cleared.")
    }

    /**
     * Fetches general disk info from Yandex Disk API
     */
    suspend fun getDiskInfo(context: Context): DiskInfoResponse = withContext(Dispatchers.IO) {
        val token = getToken(context) ?: throw IllegalStateException("Not authorized")
        api.getDiskInfo("OAuth $token")
    }

    /**
     * Creates folders on Yandex Disk if they don't already exist
     */
    private suspend fun initDirectories(authHeader: String, syncFolder: String) {
        val progressFolder = "$syncFolder/Progress"
        val paths = listOf(syncFolder, progressFolder)
        for (path in paths) {
            try {
                api.createDirectory(authHeader, path)
                Log.d(TAG, "Directory created or verified: $path")
            } catch (e: Exception) {
                // If it fails because folder already exists (typically 409), ignore
                Log.d(TAG, "Directory already exists or could not be created: $path. Error: ${e.message}")
            }
        }
    }

    /**
     * Main synchronization method
     * Performs full scan, uploads missing local books, downloads missing cloud books,
     * and syncs/merges reading progress.
     */
    private fun calculateMD5(file: File): String {
        val digest = java.security.MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun computeSha1(bytes: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-1")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun decodeBytesToString(bytes: ByteArray): String {
        return try {
            val prefixLen = minOf(bytes.size, 2048)
            val prefix = String(bytes, 0, prefixLen, Charsets.UTF_8)
            if (prefix.contains("<?xml", ignoreCase = true) || prefix.contains("<fictionbook", ignoreCase = true)) {
                String(bytes, Charsets.UTF_8)
            } else {
                String(bytes, java.nio.charset.Charset.forName("windows-1251"))
            }
        } catch (e: Exception) {
            String(bytes, Charsets.UTF_8)
        }
    }

    suspend fun calculateSyncStats(
        context: Context,
        onProgress: (status: String) -> Unit
    ): SyncStats? = withContext(Dispatchers.IO) {
        val syncFolder = getSyncFolder(context)
        val token = getToken(context) ?: return@withContext null
        val authHeader = "OAuth $token"

        try {
            onProgress("Инициализация папок в облаке...")
            initDirectories(authHeader, syncFolder)

            val database = AppDatabase.getDatabase(context)
            val repository = BookRepository(database.bookDao(), database.noteDao())
            val localBooks = repository.allBooks.first()

            onProgress("Получение списка книг из облака...")
            val cloudBooksResponse = try {
                api.getResource(authHeader, syncFolder, limit = 500)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get cloud books resource", e)
                null
            }

            val cloudItems = cloudResourceItems(cloudBooksResponse).filter { 
                val name = it.name.lowercase()
                name.endsWith(".fb2") || name.endsWith(".fb2.zip") 
            }

            onProgress("Получение манифеста...")
            var manifest = SyncManifest()
            try {
                val manifestLink = api.getDownloadLink(authHeader, "$syncFolder/sync_manifest.json")
                val body = api.downloadFile(manifestLink.href)
                val jsonStr = body.string()
                val moshi = com.squareup.moshi.Moshi.Builder().add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()
                val adapter = moshi.adapter(SyncManifest::class.java)
                manifest = adapter.fromJson(jsonStr) ?: SyncManifest()
            } catch (e: Exception) {
                Log.d(TAG, "Manifest not found or error, starting fresh")
            }

            var duplicates = 0
            val toDownload = mutableListOf<ResourceItem>()
            val toUpload = mutableListOf<BookEntity>()

            // Update manifest for files that don't have SHA1 yet by downloading them
            val totalCloudItems = cloudItems.size
            for ((index, cloudItem) in cloudItems.withIndex()) {
                if (!manifest.books.containsKey(cloudItem.name)) {
                    onProgress("Анализ файла ${cloudItem.name} (${index + 1}/$totalCloudItems)...")
                    try {
                        val linkResponse = api.getDownloadLink(authHeader, "$syncFolder/${cloudItem.name}")
                        val responseBody = api.downloadFile(linkResponse.href)
                        val bytes = responseBody.bytes()
                        
                        val ext = cloudItem.name.substringAfterLast(".").lowercase()
                        var sha1: String? = null
                        
                        if (ext == "zip") {
                            java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { zis ->
                                var entry = zis.nextEntry
                                while (entry != null) {
                                    if (!entry.isDirectory && entry.name.lowercase().endsWith(".fb2")) {
                                        val fb2Bytes = zis.readBytes()
                                        sha1 = computeSha1(fb2Bytes)
                                        break
                                    }
                                    entry = zis.nextEntry
                                }
                            }
                        } else {
                            sha1 = computeSha1(bytes)
                        }
                        
                        val finalSha1 = sha1
                        if (finalSha1 != null) {
                            manifest.books[cloudItem.name] = finalSha1
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to analyze ${cloudItem.name}", e)
                    }
                }
            }

            // Now we know all cloud SHA1s
            val cloudSha1s = manifest.books.values.toSet()

            // Calculate download list
            for (cloudItem in cloudItems) {
                val sha1 = manifest.books[cloudItem.name]
                if (sha1 != null) {
                    if (localBooks.any { it.sha1 == sha1 }) {
                        duplicates++
                    } else {
                        toDownload.add(cloudItem)
                    }
                }
            }

            // Calculate upload list
            for (localBook in localBooks) {
                if (!cloudSha1s.contains(localBook.sha1)) {
                    toUpload.add(localBook)
                }
            }

            // Also get cloud progress items so we don't have to fetch them again
            val progressResponse = try {
                api.getResource(authHeader, "$syncFolder/Progress", limit = 500)
            } catch (e: Exception) {
                null
            }
            val cloudProgressItems = cloudResourceItems(progressResponse)

            return@withContext SyncStats(
                booksOnDisk = cloudItems.size,
                booksLocal = localBooks.size,
                toDownload = toDownload,
                toUpload = toUpload,
                duplicates = duplicates,
                manifest = manifest,
                cloudProgressItems = cloudProgressItems
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating stats", e)
            return@withContext null
        }
    }

    suspend fun executeSync(
        context: Context,
        stats: SyncStats,
        onProgress: (status: String, completed: Int, total: Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val syncFolder = getSyncFolder(context)
        val token = getToken(context) ?: return@withContext false
        val authHeader = "OAuth $token"
        
        try {
            val database = AppDatabase.getDatabase(context)
            val repository = BookRepository(database.bookDao(), database.noteDao())
            
            // DOWNLOAD
            val totalDownloads = stats.toDownload.size
            var downloadedCount = 0
            for ((index, cloudItem) in stats.toDownload.withIndex()) {
                onProgress("Скачивание: ${index + 1} из $totalDownloads", index, totalDownloads)
                try {
                    val linkResponse = api.getDownloadLink(authHeader, "$syncFolder/${cloudItem.name}")
                    val responseBody = api.downloadFile(linkResponse.href)
                    val bytes = responseBody.bytes()

                    val importedFolder = File(context.filesDir, "imported_books")
                    if (!importedFolder.exists()) {
                        importedFolder.mkdirs()
                    }
                    val localFile = File(importedFolder, cloudItem.name)
                    localFile.writeBytes(bytes)

                    val ext = localFile.extension.lowercase()
                    val sha1 = stats.manifest.books[cloudItem.name] ?: continue
                    
                    var content = ""
                    var title = cloudItem.name.substringBeforeLast(".")
                    var author = "Неизвестен"
                    var series: String? = null
                    var seriesIndex: Int? = null
                    var language: String? = "ru"
                    var annotation: String? = null

                    if (ext == "zip") {
                        java.util.zip.ZipInputStream(localFile.inputStream().buffered()).use { zis ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                if (!entry.isDirectory && entry.name.lowercase().endsWith(".fb2")) {
                                    val fb2Bytes = zis.readBytes()
                                    content = decodeBytesToString(fb2Bytes)
                                    val meta = NewFb2Parser.parse(content, entry.name.substringBeforeLast("."))
                                    title = meta.title
                                    author = meta.author
                                    content = meta.content
                                    series = meta.series
                                    seriesIndex = meta.seriesIndex
                                    language = meta.language
                                    annotation = meta.annotation
                                    break
                                }
                                entry = zis.nextEntry
                            }
                        }
                    } else if (ext == "fb2") {
                        val fb2Bytes = bytes
                        content = decodeBytesToString(fb2Bytes)
                        val meta = NewFb2Parser.parse(content, title)
                        title = meta.title
                        author = meta.author
                        content = meta.content
                        series = meta.series
                        seriesIndex = meta.seriesIndex
                        language = meta.language
                        annotation = meta.annotation
                    }

                    val coverPath = NewCoverExtractor.extractAndSaveCover(content, sha1, context)
                    val strippedContent = NewCoverExtractor.stripBinarySections(content)
                    
                    val newBook = BookEntity(
                        sha1 = sha1,
                        title = title,
                        author = author,
                        content = strippedContent,
                        category = "Локальные",
                        totalCharacters = strippedContent.length,
                        coverGradientStart = getRandomGradientStartColor(),
                        coverGradientEnd = getRandomGradientEndColor(),
                        filePath = localFile.absolutePath,
                        series = series,
                        seriesIndex = seriesIndex,
                        language = language,
                        annotation = annotation,
                        fileSize = bytes.size.toLong(),
                        coverPath = coverPath
                    )
                    repository.insertBook(newBook)
                    downloadedCount++
                } catch (e: Exception) {
                    Log.e(TAG, "Error downloading book: ${cloudItem.name}", e)
                }
            }

            // UPLOAD
            val totalUploads = stats.toUpload.size
            var uploadedCount = 0
            for ((index, localBook) in stats.toUpload.withIndex()) {
                val localFile = localBook.filePath?.let { File(it) }
                if (localFile != null && localFile.exists()) {
                    val filename = localFile.name
                    onProgress("Загрузка: ${index + 1} из $totalUploads", index, totalUploads)
                    try {
                        val linkResponse = api.getUploadLink(authHeader, "$syncFolder/$filename")
                        val fileBytes = localFile.readBytes()
                        api.uploadFile(linkResponse.href, fileBytes.toRequestBody("application/octet-stream".toMediaType()))
                        
                        stats.manifest.books[filename] = localBook.sha1
                        uploadedCount++
                    } catch (e: Exception) {
                        Log.e(TAG, "Error uploading book: $filename", e)
                    }
                }
            }

            // UPLOAD MANIFEST
            onProgress("Обновление манифеста...", 0, 1)
            try {
                val moshi = com.squareup.moshi.Moshi.Builder().add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()
                val adapter = moshi.adapter(SyncManifest::class.java)
                val jsonStr = adapter.toJson(stats.manifest)
                val linkResponse = api.getUploadLink(authHeader, "$syncFolder/sync_manifest.json")
                api.uploadFile(linkResponse.href, jsonStr.toByteArray(StandardCharsets.UTF_8).toRequestBody("application/json".toMediaType()))
            } catch (e: Exception) {
                Log.e(TAG, "Error uploading manifest", e)
            }

            // SYNC PROGRESS
            onProgress("Синхронизация прогресса чтения...", 0, 1)
            val progressAdapter = moshi.adapter(BookProgressPayload::class.java)

            for (progressItem in stats.cloudProgressItems) {
                if (progressItem.type == "file" && progressItem.name.endsWith(".json")) {
                    try {
                        val linkResponse = api.getDownloadLink(authHeader, "$syncFolder/Progress/${progressItem.name}")
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
                        Log.e(TAG, "Error syncing progress file: ${progressItem.name}", e)
                    }
                }
            }

            val updatedLocalBooks = repository.allBooks.first()
            for (localBook in updatedLocalBooks) {
                val cloudProgressName = "progress_${localBook.sha1}.json"
                val matchingCloudProgress = stats.cloudProgressItems.find { it.name == cloudProgressName }

                var shouldUploadProgress = false
                if (matchingCloudProgress == null) {
                    shouldUploadProgress = localBook.currentProgressChar > 0
                } else {
                    shouldUploadProgress = true
                }

                if (shouldUploadProgress) {
                    try {
                        val payload = BookProgressPayload(
                            sha1 = localBook.sha1,
                            title = localBook.title,
                            currentProgressChar = localBook.currentProgressChar,
                            lastReadTime = localBook.lastReadTime
                        )
                        val json = progressAdapter.toJson(payload)
                        val link = api.getUploadLink(authHeader, "$syncFolder/Progress/$cloudProgressName")
                        api.uploadFile(link.href, json.toByteArray(StandardCharsets.UTF_8).toRequestBody("application/json".toMediaType()))
                    } catch (e: Exception) {
                        Log.e(TAG, "Error pushing progress: ${localBook.title}", e)
                    }
                }
            }

            saveSyncTimestamp(context)
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error during execution", e)
            return@withContext false
        }
    }
    /**
     * Directly pushes reading progress for a single book to Yandex Disk
     */
    suspend fun pushProgressToCloud(context: Context, sha1: String, progressChar: Int) = withContext(Dispatchers.IO) {
        val syncFolder = getSyncFolder(context)
        val token = getToken(context) ?: return@withContext
        val authHeader = "OAuth $token"

        try {
            val database = AppDatabase.getDatabase(context)
            val book = database.bookDao().getBookBySha1(sha1) ?: return@withContext

            val payload = BookProgressPayload(
                sha1 = sha1,
                title = book.title,
                currentProgressChar = progressChar,
                lastReadTime = System.currentTimeMillis()
            )

            val progressAdapter = moshi.adapter(BookProgressPayload::class.java)
            val json = progressAdapter.toJson(payload)

            val cloudProgressName = "progress_$sha1.json"
            val link = api.getUploadLink(authHeader, "$syncFolder/Progress/$cloudProgressName")
            api.uploadFile(link.href, json.toByteArray(StandardCharsets.UTF_8).toRequestBody("application/json".toMediaType()))
            Log.d(TAG, "Direct push progress for book $sha1 successful.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to push single progress to cloud", e)
        }
    }

    private fun cloudResourceItems(response: ResourceResponse?): List<ResourceItem> {
        return response?.embedded?.items?.filter { it.type == "file" } ?: emptyList()
    }

    private fun getRandomGradientStartColor(): String {
        val colors = listOf("#E0A96D", "#D4A373", "#CCA43B", "#C5A880", "#B5838D", "#E5989B")
        return colors.random()
    }

    private fun getRandomGradientEndColor(): String {
        val colors = listOf("#201A15", "#432818", "#3D348B", "#6F4E37", "#582F0E", "#6A4C93")
        return colors.random()
    }

    fun saveSyncTimestamp(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong("last_sync_time", System.currentTimeMillis()).apply()
    }

    fun getLastSyncTimestamp(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong("last_sync_time", 0L)
    }
}
