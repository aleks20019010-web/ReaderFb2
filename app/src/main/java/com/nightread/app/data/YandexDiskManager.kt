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
    private suspend fun initDirectories(authHeader: String) {
        val paths = listOf("disk:/SmartReader", "disk:/SmartReader/Books", "disk:/SmartReader/Progress")
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
    suspend fun syncWithCloud(
        context: Context,
        onProgress: (status: String, completed: Int, total: Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val token = getToken(context) ?: return@withContext false
        val authHeader = "OAuth $token"

        try {
            onProgress("Инициализация папок в облаке...", 0, 1)
            initDirectories(authHeader)

            val database = AppDatabase.getDatabase(context)
            val repository = BookRepository(database.bookDao(), database.noteDao())
            val localBooks = repository.allBooks.first()

            // 1. Get list of cloud books
            onProgress("Получение списка книг из облака...", 0, 1)
            val cloudBooksResponse = try {
                api.getResource(authHeader, "disk:/SmartReader/Books", limit = 500)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get cloud books resource, maybe folder is empty or not created yet", e)
                null
            }

            val cloudItems = cloudResourceItems(cloudBooksResponse)
            val cloudBookNames = cloudItems.associateBy { it.name }

            // 2. Upload missing local books to Yandex Disk
            val booksToUpload = localBooks.filter { localBook ->
                val localFile = localBook.filePath?.let { File(it) }
                val existsLocally = localFile != null && localFile.exists()
                if (existsLocally) {
                    val ext = localFile!!.extension.lowercase().ifBlank { "fb2" }
                    val cloudName = "${localBook.sha1}.$ext"
                    !cloudBookNames.containsKey(cloudName)
                } else {
                    false
                }
            }

            var uploadCount = 0
            val totalUploads = booksToUpload.size
            for (localBook in booksToUpload) {
                onProgress("Загрузка книги: ${localBook.title}", uploadCount, totalUploads)
                val localFile = File(localBook.filePath!!)
                val ext = localFile.extension.lowercase().ifBlank { "fb2" }
                val cloudName = "${localBook.sha1}.$ext"

                try {
                    val linkResponse = api.getUploadLink(authHeader, "disk:/SmartReader/Books/$cloudName")
                    val fileBytes = localFile.readBytes()
                    api.uploadFile(linkResponse.href, fileBytes.toRequestBody("application/octet-stream".toMediaType()))
                    Log.d(TAG, "Uploaded book: ${localBook.title}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error uploading book: ${localBook.title}", e)
                }
                uploadCount++
            }

            // 3. Download missing cloud books to local storage
            val booksToDownload = cloudItems.filter { cloudItem ->
                val sha1 = cloudItem.name.substringBeforeLast(".")
                localBooks.none { it.sha1 == sha1 }
            }

            var downloadCount = 0
            val totalDownloads = booksToDownload.size
            for (cloudItem in booksToDownload) {
                onProgress("Скачивание книги: ${cloudItem.name}", downloadCount, totalDownloads)
                val sha1 = cloudItem.name.substringBeforeLast(".")
                val ext = cloudItem.name.substringAfterLast(".", "fb2").lowercase()

                try {
                    val linkResponse = api.getDownloadLink(authHeader, "disk:/SmartReader/Books/${cloudItem.name}")
                    val responseBody = api.downloadFile(linkResponse.href)
                    val bytes = responseBody.bytes()

                    val importedFolder = File(context.filesDir, "imported_books")
                    if (!importedFolder.exists()) {
                        importedFolder.mkdirs()
                    }
                    val localFile = File(importedFolder, "$sha1.$ext")
                    localFile.writeBytes(bytes)

                    // Parse book details and save to local DB
                    var title = cloudItem.name.substringBeforeLast(".")
                    var author = "Неизвестен"
                    var content = ""
                    var series: String? = null
                    var seriesIndex: Int? = null
                    var language: String? = "ru"
                    var annotation: String? = null

                    if (ext == "fb2") {
                        val text = String(bytes, StandardCharsets.UTF_8)
                        val meta = NewFb2Parser.parse(text, title)
                        title = meta.title
                        author = meta.author
                        content = meta.content
                        series = meta.series
                        seriesIndex = meta.seriesIndex
                        language = meta.language
                        annotation = meta.annotation
                    } else {
                        content = String(bytes, StandardCharsets.UTF_8)
                        author = "Импорт из Облака"
                    }

                    val coverPath = NewCoverExtractor.extractAndSaveCover(content, sha1, context)
                    val strippedContent = if (ext == "fb2") {
                        NewCoverExtractor.stripBinarySections(content)
                    } else {
                        content
                    }

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
                    Log.d(TAG, "Downloaded and saved book: $title")
                } catch (e: Exception) {
                    Log.e(TAG, "Error downloading book: ${cloudItem.name}", e)
                }
                downloadCount++
            }

            // 4. Reading Progress Synchronization
            onProgress("Синхронизация прогресса чтения...", 0, 1)
            val progressResponse = try {
                api.getResource(authHeader, "disk:/SmartReader/Progress", limit = 500)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get progress folder items", e)
                null
            }

            val progressItems = cloudResourceItems(progressResponse)
            val progressAdapter = moshi.adapter(BookProgressPayload::class.java)

            // Merge cloud progress files into local database
            for (progressItem in progressItems) {
                if (progressItem.type == "file" && progressItem.name.endsWith(".json")) {
                    try {
                        val linkResponse = api.getDownloadLink(authHeader, "disk:/SmartReader/Progress/${progressItem.name}")
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
                                    Log.d(TAG, "Updated local progress for ${localBook.title} from cloud: Offset=${cloudProgress.currentProgressChar}")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error syncing progress file: ${progressItem.name}", e)
                    }
                }
            }

            // Upload any newer local progress to cloud
            val updatedLocalBooks = repository.allBooks.first()
            for (localBook in updatedLocalBooks) {
                val cloudProgressName = "progress_${localBook.sha1}.json"
                val matchingCloudProgress = progressItems.find { it.name == cloudProgressName }

                var shouldUploadProgress = false
                if (matchingCloudProgress == null) {
                    // Progress file doesn't exist on cloud at all
                    shouldUploadProgress = localBook.currentProgressChar > 0
                } else {
                    // It exists, let's compare lastReadTime or offset if timestamp is missing
                    // Downloading each cloud file to check is slow, so we can assume if local progress was updated recently, we push it.
                    // Ideally, we push progress onPause, but to be completely safe during full sync, we upload.
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
                        val link = api.getUploadLink(authHeader, "disk:/SmartReader/Progress/$cloudProgressName")
                        api.uploadFile(link.href, json.toByteArray(StandardCharsets.UTF_8).toRequestBody("application/json".toMediaType()))
                        Log.d(TAG, "Pushed local progress for ${localBook.title} to cloud: Offset=${localBook.currentProgressChar}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error pushing local progress to cloud during sync: ${localBook.title}", e)
                    }
                }
            }

            onProgress("Синхронизация успешно завершена!", 1, 1)
            saveSyncTimestamp(context)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error during full sync", e)
            onProgress("Ошибка синхронизации: ${e.localizedMessage}", 0, 1)
            false
        }
    }

    /**
     * Directly pushes reading progress for a single book to Yandex Disk
     */
    suspend fun pushProgressToCloud(context: Context, sha1: String, progressChar: Int) = withContext(Dispatchers.IO) {
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
            val link = api.getUploadLink(authHeader, "disk:/SmartReader/Progress/$cloudProgressName")
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
