package com.nightread.app.syncprogress

import androidx.room.*
import java.util.Date

/**
 * Локальное хранилище: Книга с дедупликацией по SHA-1
 */
@Entity(
    tableName = "sync_books_progress",
    indices = [Index(value = ["sha1"], unique = true)]
)
data class Book(
    @PrimaryKey val sha1: String, // Уникальный идентификатор книги = SHA1 файла (первичный ключ)
    val title: String,
    val path: String, // Путь к файлу
    val size: Long, // Размер файла
    val modified: String, // Дата изменения в ISO 8601 формате
    val isDeleted: Boolean = false, // Пометка soft delete
    val author: String = "Неизвестен",
    val totalPages: Int = 100
)

/**
 * Локальное хранилище: Прогресс чтения
 */
@Entity(
    tableName = "sync_reading_progress",
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["sha1"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ReadingProgress(
    @PrimaryKey val bookId: String, // Соответствует Book.sha1
    val currentPage: Int,
    val percent: Double,
    val lastReadDate: Date, // Хранится через TypeConverter в ISO 8601
    val deviceId: String
)

/**
 * Локальное хранилище: Кеш SHA1
 */
@Entity(
    tableName = "sync_sha1_cache",
    primaryKeys = ["accountId", "filePath"]
)
data class Sha1Cache(
    val accountId: String,
    val filePath: String,
    val fileSize: Long,
    val fileModified: String, // Время изменения в ISO 8601 формате от API
    val sha1: String,
    val cachedAt: Long = System.currentTimeMillis()
)

/**
 * Метаданные из облака (Яндекс Диск)
 */
data class CloudMetadata(
    val path: String,
    val size: Long,
    val modified: String, // ISO 8601
    val sha1: String?
)

/**
 * Конвертер типов для хранения Date в формате ISO 8601 в БД Room
 */
class Iso8601TypeConverter {
    private val formatter = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }

    @TypeConverter
    fun toDate(value: String?): Date? {
        if (value == null) return null
        return try {
            formatter.parse(value)
        } catch (e: Exception) {
            try {
                // Альтернативный разбор ISO 8601 с миллисекундами/таймзоной
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    Date.from(java.time.Instant.parse(value))
                } else {
                    java.util.Date(value.toLong())
                }
            } catch (ex: Exception) {
                null
            }
        }
    }

    @TypeConverter
    fun fromDate(date: Date?): String? {
        if (date == null) return null
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            java.time.Instant.ofEpochMilli(date.time).toString()
        } else {
            formatter.format(date)
        }
    }
}
