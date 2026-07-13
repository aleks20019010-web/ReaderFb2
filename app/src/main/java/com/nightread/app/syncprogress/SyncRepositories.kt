package com.nightread.app.syncprogress

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.nightread.app.data.YandexDiskApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Date
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "SyncRepos"

/**
 * Репозиторий для управления кэшированием SHA-1 хэшей с Яндекс Диска.
 */
class Sha1CacheRepository(
    private val context: Context,
    private val dao: SyncSha1CacheDao,
    private val yandexDiskApi: YandexDiskApi
) {
    private val cacheHits = AtomicLong(0)
    private val cacheMisses = AtomicLong(0)

    fun getCacheHits(): Long = cacheHits.get()
    fun getCacheMisses(): Long = cacheMisses.get()
    
    fun resetStats() {
        cacheHits.set(0)
        cacheMisses.set(0)
    }

    /**
     * Возвращает кэшированный SHA-1, если он валиден (размер и дата изменения совпадают).
     * Требование 3: Инвалидация при изменении fileSize или fileModified.
     */
    suspend fun getCachedSha1(
        accountId: String,
        filePath: String,
        fileSize: Long,
        fileModified: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val cached = dao.getCache(accountId, filePath)
            if (cached != null) {
                if (cached.fileSize == fileSize && cached.fileModified == fileModified) {
                    cacheHits.incrementAndGet()
                    Log.d(TAG, "Cache HIT: $filePath (sha1=${cached.sha1})")
                    cached.sha1
                } else {
                    cacheMisses.incrementAndGet()
                    Log.d(TAG, "Cache MISS (invalidated): $filePath (size changed or modified)")
                    dao.deleteCache(accountId, filePath) // Удаляем старый некорректный кэш
                    null
                }
            } else {
                cacheMisses.incrementAndGet()
                Log.d(TAG, "Cache MISS (not found): $filePath")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching cached SHA1", e)
            null
        }
    }

    /**
     * Сохранение SHA1 в кэш.
     */
    suspend fun updateCache(
        accountId: String,
        filePath: String,
        fileSize: Long,
        fileModified: String,
        sha1: String
    ) = withContext(Dispatchers.IO) {
        try {
            val entry = Sha1Cache(
                accountId = accountId,
                filePath = filePath,
                fileSize = fileSize,
                fileModified = fileModified,
                sha1 = sha1,
                cachedAt = System.currentTimeMillis()
            )
            dao.insertCache(entry)
            Log.d(TAG, "Cache saved: $filePath -> $sha1")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving SHA1 to cache", e)
        }
    }

    /**
     * Инвалидация кэша конкретного файла.
     */
    suspend fun invalidate(accountId: String, filePath: String) = withContext(Dispatchers.IO) {
        try {
            dao.deleteCache(accountId, filePath)
            Log.d(TAG, "Cache invalidated for: $filePath")
        } catch (e: Exception) {
            Log.e(TAG, "Error invalidating cache for $filePath", e)
        }
    }

    /**
     * Очистка просроченного кэша (старше 7 дней).
     * Требование 4: Автоочистка кеша раз в 7 дней.
     */
    suspend fun cleanExpiredCache() = withContext(Dispatchers.IO) {
        try {
            val weekInMs = 7L * 24 * 60 * 60 * 1000
            val threshold = System.currentTimeMillis() - weekInMs
            dao.deleteExpired(threshold)
            Log.i(TAG, "Expired SHA1 cache cleaned up.")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning expired cache", e)
        }
    }

    /**
     * Полный сброс кэша при смене аккаунта.
     * Требование 4: При смене аккаунта — полный сброс.
     */
    suspend fun resetCacheForAccount(accountId: String) = withContext(Dispatchers.IO) {
        try {
            dao.deleteByAccount(accountId)
            Log.i(TAG, "Cleared SHA1 cache for account: $accountId")
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting account cache", e)
        }
    }

    /**
     * Возвращает или вычисляет SHA1. Скачивает временный файл с таймаутом 30 секунд.
     * Использует потоковое вычисление SHA1 с буфером 8KB.
     */
    suspend fun getOrComputeSha1(
        authToken: String,
        accountId: String,
        filePath: String,
        fileSize: Long,
        fileModified: String
    ): String? = withContext(Dispatchers.IO) {
        val cached = getCachedSha1(accountId, filePath, fileSize, fileModified)
        if (cached != null) return@withContext cached

        var tempFile: File? = null
        try {
            val authHeader = if (authToken.startsWith("OAuth ")) authToken else "OAuth $authToken"
            
            // Получаем ссылку на загрузку
            val link = withTimeout(15000L) {
                yandexDiskApi.getDownloadLink(authHeader, filePath)
            }

            // Создаем временный файл
            tempFile = File.createTempFile("sha1_download_", ".tmp", context.cacheDir)

            // Скачиваем файл с таймаутом 30 секунд
            withTimeout(30000L) {
                val responseBody = yandexDiskApi.downloadFile(link.href)
                responseBody.byteStream().use { inputStream ->
                    tempFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }

            // Вычисляем SHA-1 по буферу 8KB
            val sha1 = computeFileSha1(tempFile)
            if (sha1 != null) {
                updateCache(accountId, filePath, fileSize, fileModified, sha1)
                return@withContext sha1
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.w(TAG, "Download and compute cancelled for $filePath")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error processing cloud file SHA-1 for: $filePath", e)
        } finally {
            try {
                tempFile?.delete()
            } catch (ignored: Exception) {}
        }
        null
    }

    /**
     * Пример вычисления SHA1 через FileInputStream с буфером 8KB
     */
    fun computeFileSha1(file: File): String? {
        if (!file.exists() || !file.isFile) return null
        return try {
            java.io.FileInputStream(file).use { fis ->
                val digest = MessageDigest.getInstance("SHA-1")
                val buffer = ByteArray(8192) // Буфер 8KB
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
                val sha1Bytes = digest.digest()
                sha1Bytes.joinToString("") { "%02x".format(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compute SHA-1 of local file: ${file.absolutePath}", e)
            null
        }
    }
}

/**
 * Репозиторий для сохранения прогресса чтения.
 */
class ProgressRepository(
    private val progressDao: SyncReadingProgressDao,
    private val bookDao: SyncBookDao
) {
    private val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    private var debounceJob: kotlinx.coroutines.Job? = null

    fun getLocalProgressFlow(bookId: String): Flow<ReadingProgress?> = flow {
        emit(progressDao.getProgressForBook(bookId))
    }

    suspend fun getProgressForBook(bookId: String): ReadingProgress? = withContext(Dispatchers.IO) {
        progressDao.getProgressForBook(bookId)
    }

    suspend fun getAllLocalProgress(): List<ReadingProgress> = withContext(Dispatchers.IO) {
        progressDao.getAllProgressSync()
    }

    suspend fun saveProgressDirectly(progress: ReadingProgress) = withContext(Dispatchers.IO) {
        progressDao.insertProgress(progress)
    }

    /**
     * Автосохранение при перелистывании страницы (debounce 2 секунды).
     */
    fun saveProgressWithDebounce(progress: ReadingProgress) {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            try {
                kotlinx.coroutines.delay(2000L) // задержка 2 секунды
                saveProgressDirectly(progress)
                Log.d(TAG, "Debounced local reading progress saved: $progress")
            } catch (e: Exception) {
                Log.e(TAG, "Error inside debounced coroutine", e)
            }
        }
    }

    suspend fun findBookBySha1(sha1: String): Book? = withContext(Dispatchers.IO) {
        bookDao.findBySha1(sha1)
    }

    suspend fun saveBook(book: Book) = withContext(Dispatchers.IO) {
        bookDao.insertOrUpdate(book)
    }

    /**
     * Метод addBookIfNotExists с проверкой дубликатов по SHA-1.
     * Если запись с таким SHA1 есть — НЕ создаём новую, а обновляем путь к существующей (если изменился).
     * Если нет — создаём новую запись.
     */
    suspend fun addBookIfNotExists(cloudFile: CloudMetadata, sha1: String): Book = withContext(Dispatchers.IO) {
        val existingBook = bookDao.findBySha1(sha1)
        if (existingBook != null) {
            if (existingBook.path != cloudFile.path || existingBook.isDeleted) {
                // Если путь изменился (например, файл перемещён) — обновляем путь в существующей записи, прогресс сохраняется.
                val updatedBook = existingBook.copy(
                    path = cloudFile.path,
                    isDeleted = false,
                    size = cloudFile.size,
                    modified = cloudFile.modified
                )
                bookDao.insertOrUpdate(updatedBook)
                Log.i(TAG, "Book with SHA-1 $sha1 already exists. Updated path/properties: ${cloudFile.path}")
                updatedBook
            } else {
                existingBook
            }
        } else {
            val fileName = cloudFile.path.substringAfterLast("/")
            val newBook = Book(
                sha1 = sha1,
                title = fileName.substringBeforeLast("."),
                path = cloudFile.path,
                size = cloudFile.size,
                modified = cloudFile.modified,
                isDeleted = false
            )
            bookDao.insertOrUpdate(newBook)
            Log.i(TAG, "Created new book with SHA-1 $sha1: ${newBook.title}")
            newBook
        }
    }
}

/**
 * Структура JSON-файла для загрузки/выгрузки прогресса на Яндекс Диск.
 */
@com.squareup.moshi.JsonClass(generateAdapter = true)
data class ReadingProgressPayload(
    val bookId: String,
    val currentPage: Int,
    val percent: Double,
    val lastReadDateIso: String,
    val deviceId: String
)

/**
 * Репозиторий для облачной синхронизации.
 */
class CloudSyncRepository(
    private val context: Context,
    private val yandexDiskApi: YandexDiskApi
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(ReadingProgressPayload::class.java)

    /**
     * Проверка наличия интернета.
     */
    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Запуск вызова с экспоненциальной задержкой.
     * Требование: Ошибки API (retry с экспоненциальной задержкой).
     */
    suspend fun <T> retryWithExponentialBackoff(
        initialDelay: Long = 1000L,
        factor: Double = 2.0,
        maxDelay: Long = 10000L,
        times: Int = 3,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelay
        repeat(times - 1) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w(TAG, "Yandex API call failed (attempt ${attempt + 1}). Retrying in $currentDelay ms...", e)
                kotlinx.coroutines.delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
            }
        }
        return block() // Последняя попытка
    }

    /**
     * Пример загрузки прогресса в JSON на Яндекс Диск.
     */
    suspend fun uploadReadingProgress(
        authToken: String,
        progress: ReadingProgress
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable()) {
            Log.w(TAG, "No internet connection to upload reading progress")
            return@withContext false
        }

        try {
            val authHeader = if (authToken.startsWith("OAuth ")) authToken else "OAuth $authToken"
            
            // Превращаем в JSON Payload
            val formatter = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            val payload = ReadingProgressPayload(
                bookId = progress.bookId,
                currentPage = progress.currentPage,
                percent = progress.percent,
                lastReadDateIso = formatter.format(progress.lastReadDate),
                deviceId = progress.deviceId
            )
            val json = adapter.toJson(payload)
            val requestBody = json.toRequestBody("application/json".toMediaType())

            // Ссылка на выгрузку файла прогресса, например: /NightRead_Progress_bookId.json
            val cloudPath = "/NightRead_Progress_${progress.bookId}.json"

            retryWithExponentialBackoff {
                val uploadLink = yandexDiskApi.getUploadLink(authHeader, cloudPath, overwrite = true)
                val response = yandexDiskApi.uploadFile(uploadLink.href, requestBody)
                if (!response.isSuccessful) {
                    throw IOException("HTTP error uploading progress: ${response.code()}")
                }
            }
            Log.d(TAG, "Successfully uploaded progress for book ${progress.bookId} to Yandex Disk")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload reading progress", e)
            false
        }
    }

    /**
     * Пример выгрузки прогресса из JSON на Яндекс Диске.
     */
    suspend fun downloadReadingProgress(
        authToken: String,
        bookId: String
    ): ReadingProgress? = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable()) {
            Log.w(TAG, "No internet connection to download reading progress")
            return@withContext null
        }

        try {
            val authHeader = if (authToken.startsWith("OAuth ")) authToken else "OAuth $authToken"
            val cloudPath = "/NightRead_Progress_${bookId}.json"

            val jsonContent = retryWithExponentialBackoff {
                // Проверяем, существует ли файл. Если нет, API выкинет ошибку или вернет 404.
                val downloadLink = yandexDiskApi.getDownloadLink(authHeader, cloudPath)
                val responseBody = yandexDiskApi.downloadFile(downloadLink.href)
                responseBody.string()
            }

            val payload = adapter.fromJson(jsonContent) ?: return@withContext null
            val formatter = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            val parsedDate = try {
                formatter.parse(payload.lastReadDateIso) ?: Date()
            } catch (e: Exception) {
                Date()
            }

            ReadingProgress(
                bookId = payload.bookId,
                currentPage = payload.currentPage,
                percent = payload.percent,
                lastReadDate = parsedDate,
                deviceId = payload.deviceId
            )
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 404) {
                Log.d(TAG, "No progress file found on cloud for book: $bookId")
            } else {
                Log.e(TAG, "HTTP error downloading reading progress", e)
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download reading progress for book: $bookId", e)
            null
        }
    }
}
