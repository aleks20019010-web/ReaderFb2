package com.nightread.app.data

import android.content.Context
import android.util.Log
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CancellationException
import okhttp3.ResponseBody
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "Sha1Cache"

/**
 * Модель записи кэша для хэшей SHA-1 файлов с Яндекс Диска.
 */
@Entity(
    tableName = "sha1_cache",
    indices = [Index(value = ["compositeKey"], unique = true)]
)
data class CacheEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val compositeKey: String, // Формат: "${accountId}_${filePath}_${fileSize}_${fileModifiedTime}"
    val accountId: String,
    val path: String,
    val size: Long,
    val modified: String,
    val sha1: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * DAO интерфейс для взаимодействия с таблицей sha1_cache.
 */
@Dao
interface Sha1CacheDao {

    @Query("SELECT * FROM sha1_cache WHERE compositeKey = :key LIMIT 1")
    suspend fun getByKey(key: String): CacheEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: CacheEntry)

    @Query("DELETE FROM sha1_cache WHERE accountId = :accountId")
    suspend fun deleteByAccount(accountId: String)

    @Query("DELETE FROM sha1_cache")
    suspend fun deleteAll()

    @Query("DELETE FROM sha1_cache WHERE accountId = :accountId AND path = :path")
    suspend fun deleteByPath(accountId: String, path: String)

    @Query("SELECT * FROM sha1_cache WHERE accountId = :accountId")
    fun getByAccountFlow(accountId: String): Flow<List<CacheEntry>>
}

/**
 * Репозиторий для управления кэшированием SHA-1 хэшей с Яндекс Диска.
 */
class Sha1CacheRepository(
    private val context: Context,
    private val dao: Sha1CacheDao,
    private val yandexDiskApi: YandexDiskApi
) {
    // Счётчики попаданий и промахов кэша
    private val cacheHits = AtomicLong(0)
    private val cacheMisses = AtomicLong(0)

    fun getCacheHits(): Long = cacheHits.get()
    fun getCacheMisses(): Long = cacheMisses.get()
    fun resetStats() {
        cacheHits.set(0)
        cacheMisses.set(0)
    }

    /**
     * Возвращает кэшированный SHA-1 по композитному ключу, если он существует.
     */
    suspend fun getCachedSha1(
        accountId: String,
        path: String,
        size: Long,
        modified: String
    ): String? = withContext(Dispatchers.IO) {
        val key = generateCompositeKey(accountId, path, size, modified)
        try {
            val entry = dao.getByKey(key)
            if (entry != null) {
                cacheHits.incrementAndGet()
                Log.d(TAG, "Cache HIT for path: $path, sha1: ${entry.sha1}")
                entry.sha1
            } else {
                cacheMisses.incrementAndGet()
                Log.d(TAG, "Cache MISS for path: $path (Key not found)")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error accessing cache db for key: $key", e)
            null
        }
    }

    /**
     * Сохраняет SHA-1 в кэш.
     */
    suspend fun updateCache(
        accountId: String,
        path: String,
        size: Long,
        modified: String,
        sha1: String
    ) = withContext(Dispatchers.IO) {
        val key = generateCompositeKey(accountId, path, size, modified)
        try {
            val entry = CacheEntry(
                compositeKey = key,
                accountId = accountId,
                path = path,
                size = size,
                modified = modified,
                sha1 = sha1
            )
            dao.insert(entry)
            Log.d(TAG, "Saved to cache: path=$path, size=$size, sha1=$sha1")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating cache db for key: $key", e)
        }
    }

    /**
     * Получает SHA-1 хэш файла. Сначала пытается взять из кэша.
     * Если не найдено (или изменилось), скачивает временный файл с таймаутом 30 секунд,
     * вычисляет хэш и сохраняет его в кэш.
     */
    suspend fun getOrComputeSha1(
        authToken: String,
        accountId: String,
        path: String,
        size: Long,
        modified: String
    ): String? = withContext(Dispatchers.IO) {
        // 1. Попытка получить из кэша
        val cached = getCachedSha1(accountId, path, size, modified)
        if (cached != null) {
            return@withContext cached
        }

        // 2. Скачивание и вычисление
        Log.d(TAG, "Cache miss. Downloading temporary file and computing SHA-1 for: $path")
        val authHeader = if (authToken.startsWith("OAuth ")) authToken else "OAuth $authToken"

        var tempFile: File? = null
        try {
            // Получаем ссылку на скачивание через API
            val linkResponse = withTimeout(15000L) {
                yandexDiskApi.getDownloadLink(authHeader, path)
            }

            tempFile = File.createTempFile("sha1_calc_", ".tmp", context.cacheDir)

            // Скачивание с таймаутом в 30 секунд
            withTimeout(30000L) {
                val responseBody = yandexDiskApi.downloadFile(linkResponse.href)
                responseBody.byteStream().use { inputStream ->
                    tempFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }

            // Вычисление SHA-1 с использованием буфера 8KB
            val sha1 = computeFileSha1(tempFile)
            if (sha1 != null) {
                updateCache(accountId, path, size, modified, sha1)
                return@withContext sha1
            }
        } catch (e: CancellationException) {
            Log.w(TAG, "Coroutine download/compute canceled for path: $path")
            throw e
        } catch (e: IOException) {
            Log.e(TAG, "IO or Network Exception occurred while syncing file SHA-1 for: $path", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error getting/computing SHA-1 for: $path", e)
        } finally {
            try {
                tempFile?.delete()
            } catch (ignored: Exception) {}
        }
        null
    }

    /**
     * Инвалидация конкретного файла в кэше.
     */
    suspend fun invalidate(accountId: String, path: String) = withContext(Dispatchers.IO) {
        try {
            dao.deleteByPath(accountId, path)
            Log.d(TAG, "Invalidated cache entry for: $path (account: $accountId)")
        } catch (e: Exception) {
            Log.e(TAG, "Error invalidating path: $path", e)
        }
    }

    /**
     * Очистка всего кэша для конкретного аккаунта (например, при выходе).
     */
    suspend fun invalidateAccount(accountId: String) = withContext(Dispatchers.IO) {
        try {
            dao.deleteByAccount(accountId)
            Log.i(TAG, "Cleared SHA-1 cache for account: $accountId")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing account cache: $accountId", e)
        }
    }

    /**
     * Полная очистка кэша (принудительный сброс).
     */
    suspend fun invalidateAll() = withContext(Dispatchers.IO) {
        try {
            dao.deleteAll()
            Log.i(TAG, "Cleared entire SHA-1 cache database")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing entire cache database", e)
        }
    }

    /**
     * Генерация уникального композитного ключа для Room.
     */
    private fun generateCompositeKey(
        accountId: String,
        path: String,
        size: Long,
        modified: String
    ): String {
        return "${accountId}_${path}_${size}_${modified}"
    }

    /**
     * Метод потокового вычисления SHA-1 хеша файла без загрузки всего файла в память.
     * Используется буфер размером 8 КБ (8192 байт).
     */
    private fun computeFileSha1(file: File): String? {
        if (!file.exists() || !file.isFile) return null
        return try {
            file.inputStream().use { inputStream ->
                computeInputStreamSha1(inputStream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error computing file SHA-1", e)
            null
        }
    }

    fun computeInputStreamSha1(inputStream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
        val sha1Bytes = digest.digest()
        return sha1Bytes.joinToString("") { "%02x".format(it) }
    }
}
