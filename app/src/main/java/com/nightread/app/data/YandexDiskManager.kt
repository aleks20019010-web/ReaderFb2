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
import okio.buffer
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
        try {
            api.getDiskInfo("OAuth $token")
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 401) {
                Log.e("AUTH_ERROR", "getDiskInfo: Token is invalid/expired. Clearing token.")
                clearToken(context)
            }
            throw e
        } catch (e: Exception) {
            Log.e("AUTH_ERROR", "getDiskInfo: API call failed.", e)
            throw e
        }
    }

    /**
     * Проверяет токен перед сохранением, запрашивая информацию о диске.
     * Возвращает true, если токен валидный и подключение успешно.
     * При ошибке логирует её и возвращает false.
     */
    suspend fun connect(context: Context, token: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val authHeader = "OAuth $token"
            Log.d("AUTH_ERROR", "connect: Validating received token...")
            api.getDiskInfo(authHeader)
            saveToken(context, token)
            Log.d("AUTH_ERROR", "connect: Token successfully validated and saved.")
            true
        } catch (e: Exception) {
            Log.e("AUTH_ERROR", "connect: Validation failed for token. Token NOT saved.", e)
            false
        }
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

    suspend fun uploadBook(context: Context, cleanPath: String, fileBytes: ByteArray, sha1: String): Boolean = withContext(Dispatchers.IO) {
        val token = getToken(context) ?: return@withContext false
        val authHeader = "OAuth $token"
        try {
            val originalName = File(cleanPath).name
            val totalSize = fileBytes.size.toLong()
            
            YandexSyncState.update {
                it.copy(
                    currentFileName = originalName,
                    currentFileBytesTransferred = 0L,
                    currentFileTotalBytes = totalSize
                )
            }

            val linkResponse = api.getUploadLink(authHeader, cleanPath)
            
            val baseBody = fileBytes.toRequestBody("application/octet-stream".toMediaType())
            var lastUpdateBytes = 0L
            val updateThreshold = 50 * 1024 // 50 KB
            
            val progressBody = ProgressRequestBody(baseBody) { bytesWritten, total ->
                val currentTotal = if (total > 0) total else totalSize
                if (bytesWritten - lastUpdateBytes >= updateThreshold || bytesWritten == currentTotal) {
                    lastUpdateBytes = bytesWritten
                    YandexSyncState.update {
                        it.copy(
                            currentFileBytesTransferred = bytesWritten,
                            currentFileTotalBytes = currentTotal
                        )
                    }
                }
            }

            api.uploadFile(linkResponse.href, progressBody)
            
            // Получаем метаданные загруженного файла для получения даты модификации
            val metaResponse = api.getResource(authHeader, cleanPath, limit = 1)
            val modified = metaResponse.modified ?: ""
            
            // Сохраняем в кэш
            val db = AppDatabase.getDatabase(context)
            val cache = CloudFileCache(db.cloudFileDao())
            cache.save(sha1, cleanPath, modified, fileBytes.size.toLong())
            
            Log.d(TAG, "Uploaded book and cached SHA-1: $cleanPath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading book: $cleanPath", e)
            false
        } finally {
            YandexSyncState.update {
                it.copy(
                    currentFileName = null,
                    currentFileBytesTransferred = 0L,
                    currentFileTotalBytes = 0L
                )
            }
        }
    }

    private fun computeSha1(bytes: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-1")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    suspend fun pushProgressToCloud(context: Context, sha1: String, progressChar: Int) = withContext(Dispatchers.IO) {
        val syncFolder = getSyncFolder(context)
        val token = getToken(context) ?: return@withContext
        val authHeader = "OAuth $token"
        try {
            val database = AppDatabase.getDatabase(context)
            val book = database.bookDao().getBookBySha1(sha1) ?: return@withContext
            val totalChars = book.totalCharacters
            val progress = if (totalChars > 0) (progressChar.toLong() * 100 / totalChars).toInt().coerceIn(0, 100) else 0
            val payload = BookProgressPayload(
                sha1 = sha1,
                page = book.currentPageIndex,
                charOffset = progressChar,
                progress = progress,
                lastReadTime = System.currentTimeMillis(),
                totalChars = totalChars
            )
            val progressAdapter = moshi.adapter(BookProgressPayload::class.java)
            val json = progressAdapter.toJson(payload)
            val cloudProgressName = "$sha1.json"
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

    suspend fun deleteFile(context: Context, path: String): Boolean = withContext(Dispatchers.IO) {
        val token = getToken(context) ?: return@withContext false
        val authHeader = "OAuth $token"
        val cleanPath = normalizePath(path)
        try {
            Log.d(TAG, "deleteFile: Deleting resource '$cleanPath'")
            val response = api.deleteResource(authHeader, cleanPath, permanently = true)
            if (response.isSuccessful) {
                Log.d(TAG, "deleteFile successful: $cleanPath")
                true
            } else {
                Log.e(TAG, "deleteFile failed with code: ${response.code()} for path: $cleanPath")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "deleteFile failed for path: $cleanPath", e)
            false
        }
    }
}

class ProgressRequestBody(
    private val delegate: okhttp3.RequestBody,
    private val onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit
) : okhttp3.RequestBody() {

    override fun contentType(): okhttp3.MediaType? = delegate.contentType()

    override fun contentLength(): Long = try {
        delegate.contentLength()
    } catch (e: java.io.IOException) {
        -1L
    }

    override fun writeTo(sink: okio.BufferedSink) {
        val total = contentLength()
        var bytesWritten = 0L
        
        val countingSink = object : okio.ForwardingSink(sink) {
            override fun write(source: okio.Buffer, byteCount: Long) {
                super.write(source, byteCount)
                bytesWritten += byteCount
                onProgress(bytesWritten, total)
            }
        }
        val bufferedSink = countingSink.buffer()
        delegate.writeTo(bufferedSink)
        bufferedSink.flush()
    }
}
