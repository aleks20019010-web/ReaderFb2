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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

object YandexDiskManager {


    suspend fun getFolders(context: Context, path: String = "/"): List<ResourceItem> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val token = getToken(context) ?: return@withContext emptyList()
            val authHeader = "OAuth $token"
            val cleanPath = normalizePath(path)
            Log.d(TAG, "getFolders: Requesting path='$cleanPath'. Prefix 'disk:' removed.")
            
            val maskedToken = if (token.length > 8) "${token.take(4)}...${token.takeLast(4)}" else "***"
            Log.d(TAG, "getFolders Auth Header: 'OAuth $maskedToken' (Length: ${token.length})")

            try {
                val response = api.getResource(authHeader, cleanPath, limit = 500)
                Log.d(TAG, "getFolders API Response: Code 200 (Success) for path='$cleanPath'")
                response.embedded?.items?.filter { it.type == "dir" } ?: emptyList()
            } catch (e: retrofit2.HttpException) {
                val code = e.code()
                val errorBody = e.response()?.errorBody()?.string()
                Log.e(TAG, "getFolders API Error: Code $code, Message: ${e.message()}, Body: $errorBody")
                if (code == 401) {
                    Log.e(TAG, "Token expired/invalid. Clearing token.")
                    clearToken(context)
                }
                emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "getFolders Connection/Parsing Error: ${e.message}", e)
                emptyList()
            }
        }
    }

    fun normalizePath(path: String): String {
        var p = path.trim()
        if (p.startsWith("disk:")) {
            p = p.substringAfter("disk:")
        }
        if (p.isEmpty()) return "/"
        if (!p.startsWith("/")) {
            p = "/$p"
        }
        while (p.endsWith("/") && p != "/") {
            p = p.substring(0, p.length - 1)
        }
        Log.d(TAG, "normalizePath: input='$path' -> normalized='$p'")
        return p
    }

    fun getSyncFolder(context: Context): String {
        val prefs = context.getSharedPreferences("yandex_sync", Context.MODE_PRIVATE)
        val raw = prefs.getString("sync_folder", "/Books") ?: "/Books"
        return normalizePath(raw)
    }

    fun setSyncFolder(context: Context, folder: String) {
        val prefs = context.getSharedPreferences("yandex_sync", Context.MODE_PRIVATE)
        prefs.edit().putString("sync_folder", normalizePath(folder)).apply()
    }

    private const val TAG = "YandexDiskManager"
    private const val PREFS_NAME = "yandex_prefs"
    private const val KEY_TOKEN = "oauth_token"
    private const val BASE_URL = "https://cloud-api.yandex.net/"

    // Placeholder Client ID for Yandex OAuth. Users can replace this with their own.
    const val CLIENT_ID = "bfdea73d1e6242ba826f15d9d0374005"

    val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val api: YandexDiskApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(YandexDiskApi::class.java)
    }

    /**
     * Helper to compute SHA-1 of the FB2 content (unzipped if necessary)
     */
    fun computeSha1FromContent(file: File): String? {
        return Sha1Helper.computeSha1FromContent(file)
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
                Log.d(TAG, "Directory already exists or could not be created: $path. Error: ${e.message}")
            }
        }
    }

    suspend fun resolveCaseInsensitivePath(context: Context, path: String): String {
        val token = getToken(context) ?: return path
        val authHeader = "OAuth $token"
        
        val cleanPath = normalizePath(path)
        Log.d(TAG, "resolveCaseInsensitivePath: input='$path' -> cleanPath='$cleanPath'")
        if (checkPathExists(authHeader, cleanPath)) {
            Log.d(TAG, "resolveCaseInsensitivePath: Path '$cleanPath' exists as-is.")
            return cleanPath
        }
        
        Log.d(TAG, "resolveCaseInsensitivePath: Path '$cleanPath' does not exist as-is. Resolving case-insensitively.")
        val parts = cleanPath.split("/").filter { it.isNotEmpty() }
        if (parts.isEmpty()) return "/"
        
        var currentPath = "/"
        for (part in parts) {
            try {
                Log.d(TAG, "resolveCaseInsensitivePath: Resolving part '$part' in currentPath='$currentPath'")
                val response = api.getResource(
                    token = authHeader,
                    path = currentPath,
                    limit = 500,
                    fields = "_embedded.items.name,_embedded.items.type,_embedded.items.path"
                )
                Log.d(TAG, "resolveCaseInsensitivePath API Response: Code 200 (OK)")
                val dirs = response.embedded?.items?.filter { it.type == "dir" } ?: emptyList()
                val matchingDir = dirs.find { it.name.equals(part, ignoreCase = true) }
                if (matchingDir != null) {
                    val rawMatchingPath = matchingDir.path ?: "$currentPath/${matchingDir.name}"
                    currentPath = normalizePath(rawMatchingPath)
                } else {
                    currentPath = if (currentPath == "/") "/$part" else "$currentPath/$part"
                }
            } catch (e: retrofit2.HttpException) {
                Log.e(TAG, "resolveCaseInsensitivePath API Error: Code ${e.code()}, Body: ${e.response()?.errorBody()?.string()}")
                if (e.code() == 401) {
                    clearToken(context)
                }
                currentPath = if (currentPath == "/") "/$part" else "$currentPath/$part"
            } catch (e: Exception) {
                Log.e(TAG, "resolveCaseInsensitivePath Error resolving path part: $part in $currentPath", e)
                currentPath = if (currentPath == "/") "/$part" else "$currentPath/$part"
            }
        }
        val finalPath = normalizePath(currentPath)
        Log.d(TAG, "resolveCaseInsensitivePath: Resolved path from '$path' to '$finalPath'")
        return finalPath
    }

    private suspend fun checkPathExists(authHeader: String, path: String): Boolean {
        val cleanPath = normalizePath(path)
        return try {
            Log.d(TAG, "checkPathExists: Checking cleanPath='$cleanPath'")
            api.getResource(authHeader, cleanPath, limit = 1, fields = "type")
            Log.d(TAG, "checkPathExists: Path '$cleanPath' exists (Code 200)")
            true
        } catch (e: Exception) {
            Log.d(TAG, "checkPathExists: Path '$cleanPath' does not exist or failed: ${e.message}")
            false
        }
    }

    suspend fun getAllFilesFromFolder(
        context: Context?,
        authHeader: String,
        path: String,
        onProgress: ((String) -> Unit)? = null
    ): List<ResourceItem> {
        val items = mutableListOf<ResourceItem>()
        var offset = 0
        val limit = 100 // Safe, standard limit that Yandex definitely supports
        var hasMore = true
        var page = 1

        val cleanPath = normalizePath(path)
        Log.d(TAG, "getAllFilesFromFolder: Starting listing for path='$cleanPath'. Prefix 'disk:' removed.")

        while (hasMore) {
            try {
                Log.d(TAG, "getAllFilesFromFolder: Page $page, requesting offset=$offset, limit=$limit for path='$cleanPath'")
                val response = api.getResource(
                    token = authHeader,
                    path = cleanPath,
                    limit = limit,
                    offset = offset,
                    fields = null
                )
                
                Log.d(TAG, "API Response for path='$cleanPath': Code 200 (OK)")

                val embedded = response.embedded
                val pageItems = embedded?.items ?: emptyList()
                Log.d(TAG, "getAllFilesFromFolder: Page $page fetched, received ${pageItems.size} items from API (before filtering).")

                if (pageItems.isEmpty()) {
                    Log.d(TAG, "getAllFilesFromFolder: Page $page is empty. Stopping pagination.")
                    hasMore = false
                } else {
                    val fileItems = pageItems.filter { it.type == "file" }
                    items.addAll(fileItems)
                    Log.d(TAG, "getAllFilesFromFolder: Added ${fileItems.size} files. Total gathered so far: ${items.size}")

                    val total = embedded?.total
                    val returnedLimit = embedded?.limit ?: limit
                    
                    Log.d(TAG, "getAllFilesFromFolder: Pagination parameters in response: total=$total, limit=$returnedLimit, offset=${embedded?.offset}")

                    if (total != null) {
                        offset += pageItems.size
                        if (offset >= total) {
                            Log.d(TAG, "getAllFilesFromFolder: Offset $offset reached or exceeded total $total. Stopping pagination.")
                            hasMore = false
                        } else {
                            page++
                        }
                    } else {
                        // Fallback pagination when total is not returned
                        if (pageItems.size < returnedLimit) {
                            Log.d(TAG, "getAllFilesFromFolder: Received ${pageItems.size} < limit $returnedLimit. Stopping pagination.")
                            hasMore = false
                        } else {
                            offset += pageItems.size
                            page++
                        }
                    }
                }
            } catch (e: retrofit2.HttpException) {
                val code = e.code()
                val errorBody = e.response()?.errorBody()?.string()
                Log.e(TAG, "API Response Error: Code $code, Message: ${e.message()}, Body: $errorBody")
                if (code == 401 && context != null) {
                    Log.e(TAG, "API Token is invalid or expired! Clearing stored token.")
                    clearToken(context)
                }
                hasMore = false
            } catch (e: Exception) {
                Log.e(TAG, "API Connection/Parsing Error: ${e.message}", e)
                hasMore = false
            }
        }
        
        Log.d(TAG, "getAllFilesFromFolder: Completed. Total files found: ${items.size}.")
        return items
    }

    suspend fun downloadFileToTemp(context: Context, path: String, tempFile: File): Boolean = withContext(Dispatchers.IO) {
        val token = getToken(context) ?: return@withContext false
        val authHeader = "OAuth $token"
        val cleanPath = normalizePath(path)
        try {
            Log.d(TAG, "downloadFileToTemp: Downloading cleanPath='$cleanPath'")
            val linkResponse = api.getDownloadLink(authHeader, cleanPath)
            val responseBody = api.downloadFile(linkResponse.href)
            tempFile.outputStream().use { output ->
                responseBody.byteStream().use { input ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "downloadFileToTemp successful for: $cleanPath")
            true
        } catch (e: retrofit2.HttpException) {
            Log.e(TAG, "downloadFileToTemp failed with HTTP error: Code ${e.code()}. Body: ${e.response()?.errorBody()?.string()}", e)
            if (e.code() == 401) {
                clearToken(context)
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "downloadFileToTemp failed for path: $cleanPath", e)
            false
        }
    }

    private fun computeSha1(bytes: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-1")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    suspend fun calculateSyncStats(
        context: Context,
        onProgress: (status: String) -> Unit
    ): SyncStats? = withContext(Dispatchers.IO) {
        val originalFolder = getSyncFolder(context)
        val token = getToken(context) ?: return@withContext null
        val authHeader = "OAuth $token"

        try {
            val database = AppDatabase.getDatabase(context)

            onProgress("Поиск папки синхронизации...")
            val syncFolder = resolveCaseInsensitivePath(context, originalFolder)
            if (syncFolder != originalFolder) {
                setSyncFolder(context, syncFolder)
            }

            Log.d(TAG, "calculateSyncStats: Original folder='$originalFolder' resolved to='$syncFolder'")
            if (syncFolder.contains("disk:")) {
                Log.e(TAG, "calculateSyncStats ERROR: Resolved path contains 'disk:' prefix!")
            } else {
                Log.d(TAG, "calculateSyncStats: Verified resolved path '$syncFolder' does not contain 'disk:' prefix.")
            }

            val maskedToken = if (token.length > 8) "${token.take(4)}...${token.takeLast(4)}" else "***"
            Log.d(TAG, "calculateSyncStats Auth Header: 'OAuth $maskedToken' (Length: ${token.length})")

            onProgress("Подготовка облака...")
            initDirectories(authHeader, syncFolder)

            onProgress("Получение списка файлов с Яндекс Диска...")
            val cloudItems = getAllFilesFromFolder(context, authHeader, syncFolder)
            
            Log.d(TAG, "calculateSyncStats: Starting filtering of ${cloudItems.size} files.")
            if (cloudItems.isNotEmpty()) {
                Log.d(TAG, "calculateSyncStats: Showing first 5 files from disk for extension check:")
                cloudItems.take(5).forEachIndexed { idx, item ->
                    Log.d(TAG, "  [$idx] Name: '${item.name}', Type: '${item.type}', Path: '${item.path}'")
                }
            } else {
                Log.d(TAG, "calculateSyncStats: No files found in folder.")
            }

            val cloudBooks = cloudItems.filter {
                val lowerName = it.name.lowercase()
                val isFb2 = lowerName.endsWith(".fb2")
                val isFb2Zip = lowerName.endsWith(".fb2.zip")
                val isEpub = lowerName.endsWith(".epub")
                val matches = isFb2 || isFb2Zip || isEpub
                Log.v(TAG, "File: '${it.name}' -> isFb2=$isFb2, isFb2Zip=$isFb2Zip, isEpub=$isEpub -> matches=$matches")
                matches
            }
            val booksOnDisk = cloudBooks.size
            Log.d(TAG, "calculateSyncStats: Found $booksOnDisk books out of ${cloudItems.size} files on disk.")

            if (cloudBooks.isNotEmpty()) {
                Log.d(TAG, "calculateSyncStats: First 5 MATCHED books:")
                cloudBooks.take(5).forEachIndexed { idx, item ->
                    Log.d(TAG, "  [$idx] Book Name: '${item.name}', Path: '${item.path}'")
                }
            }

            val cloudCache = CloudFileCache.loadCache(context)
            val updatedCloudBooks = mutableListOf<CloudFileEntry>()
            
            // Identify books that need sha-1 calculation
            val needsSha1 = mutableListOf<ResourceItem>()
            for (cloudBook in cloudBooks) {
                val cached = cloudCache.entries[cloudBook.name]
                if (cached != null && cached.size == cloudBook.size && cached.modified == cloudBook.modified) {
                    updatedCloudBooks.add(cached)
                } else {
                    needsSha1.add(cloudBook)
                }
            }
            
            Log.d(TAG, "calculateSyncStats: Cache hits = ${updatedCloudBooks.size}, Needs SHA-1 calculation = ${needsSha1.size}")

            if (needsSha1.isNotEmpty()) {
                var processedCount = 0
                val totalToProcess = needsSha1.size
                val startTime = System.currentTimeMillis()
                
                // Parallel processing with limited concurrency
                val concurrencyLimit = 5
                val jobs = needsSha1.chunked(concurrencyLimit)
                
                for (chunk in jobs) {
                    coroutineScope {
                        val deferreds = chunk.map { item ->
                            async {
                                try {
                                    val cleanItemPath = normalizePath(item.path ?: "$syncFolder/${item.name}")
                                    val linkResponse = api.getDownloadLink(authHeader, cleanItemPath)
                                    val responseBody = api.downloadFile(linkResponse.href)
                                    
                                    val tempFile = File(context.cacheDir, "temp_${item.name}")
                                    try {
                                        tempFile.outputStream().use { output ->
                                            responseBody.byteStream().use { input ->
                                                input.copyTo(output)
                                            }
                                        }
                                        val bytes = tempFile.readBytes()
                                        val sha1 = computeSha1(bytes)
                                        val newEntry = CloudFileEntry(
                                            name = item.name,
                                            size = item.size ?: 0L,
                                            modified = item.modified ?: "",
                                            sha1 = sha1
                                        )
                                        synchronized(cloudCache.entries) {
                                            cloudCache.entries[item.name] = newEntry
                                            updatedCloudBooks.add(newEntry)
                                        }
                                    } finally {
                                        if (tempFile.exists()) {
                                            tempFile.delete() // Guarantee deletion of temp file
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error processing cloud file: ${item.name}", e)
                                } finally {
                                    synchronized(this@YandexDiskManager) {
                                        processedCount++
                                        if (processedCount % 5 == 0 || processedCount == totalToProcess) {
                                            val elapsed = System.currentTimeMillis() - startTime
                                            val avgTimePerFile = elapsed / processedCount
                                            val remaining = totalToProcess - processedCount
                                            val remainingMs = remaining * avgTimePerFile
                                            val remainingMsLong = remainingMs.toLong()
                                            val remainingMinutes = remainingMsLong / 60000
                                            val remainingSeconds = (remainingMsLong % 60000) / 1000
                                            val timeStr = if (remainingMinutes > 0) "$remainingMinutes мин" else "$remainingSeconds сек"
                                            onProgress("Анализ диска: $processedCount из $totalToProcess файлов обработано. Осталось примерно: $timeStr")
                                        }
                                    }
                                }
                            }
                        }
                        awaitAll(*deferreds.toTypedArray())
                    }
                }
                CloudFileCache.saveCache(context, cloudCache)
            }
            
            val repository = BookRepository(database.bookDao(), database.noteDao())
            val localBooks = repository.allBooks.first()
            val localSha1s = localBooks.map { it.sha1 }.toSet()

            val toDownload = updatedCloudBooks.filter { !localSha1s.contains(it.sha1) }
            val cloudSha1s = updatedCloudBooks.map { it.sha1 }.toSet()
            val toUpload = localBooks.filter { !cloudSha1s.contains(it.sha1) }
            val duplicates = updatedCloudBooks.size - toDownload.size

            onProgress("Найдено ${toDownload.size} новых книг на диске, ${toUpload.size} книг нужно загрузить на диск")

            val cloudProgressItems = getAllFilesFromFolder(context, authHeader, "$syncFolder/Progress")

            return@withContext SyncStats(
                booksOnDisk = booksOnDisk,
                booksLocal = localBooks.size,
                toDownload = toDownload,
                toUpload = toUpload,
                duplicates = duplicates,
                cloudProgressItems = cloudProgressItems
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating sync stats", e)
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
            for ((index, cloudItem) in stats.toDownload.withIndex()) {
                onProgress("Скачивание: ${index + 1} из $totalDownloads", index, totalDownloads)
                try {
                    val cleanPath = normalizePath("$syncFolder/${cloudItem.name}")
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
                    } finally {
                        if (tempFile.exists()) tempFile.delete() // Guarantee deletion
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error downloading book: ${cloudItem.name}", e)
                }
            }
            
            // UPLOAD
            val totalUploads = stats.toUpload.size
            for ((index, localBook) in stats.toUpload.withIndex()) {
                val localFile = localBook.filePath?.let { File(it) }
                if (localFile != null && localFile.exists()) {
                    val ext = if (localFile.name.endsWith(".zip")) ".fb2.zip" else ".fb2"
                    val filename = "${localBook.sha1}$ext"
                    onProgress("Загрузка: ${index + 1} из $totalUploads", index, totalUploads)
                    try {
                        val cleanPath = normalizePath("$syncFolder/$filename")
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
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "Error uploading book: $filename", e)
                    }
                }
            }
            
            // SYNC PROGRESS
            onProgress("Синхронизация прогресса чтения...", 0, 1)
            val progressAdapter = moshi.adapter(BookProgressPayload::class.java)
            for (progressItem in stats.cloudProgressItems) {
                if (progressItem.name.endsWith(".json")) {
                    try {
                        val cleanPath = normalizePath(progressItem.path ?: "$syncFolder/Progress/${progressItem.name}")
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
                        val cleanPath = normalizePath("$syncFolder/Progress/$cloudProgressName")
                        val link = api.getUploadLink(authHeader, cleanPath)
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
            val cleanPath = normalizePath("$syncFolder/Progress/$cloudProgressName")
            val link = api.getUploadLink(authHeader, cleanPath)
            api.uploadFile(link.href, json.toByteArray(StandardCharsets.UTF_8).toRequestBody("application/json".toMediaType()))
            Log.d(TAG, "Direct push progress for book $sha1 successful.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to push single progress to cloud", e)
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

    fun saveSyncTimestamp(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong("last_sync_time", System.currentTimeMillis()).apply()
    }

    fun getLastSyncTimestamp(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong("last_sync_time", 0L)
    }
}
