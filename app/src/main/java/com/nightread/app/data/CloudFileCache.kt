package com.nightread.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Модель для кэширования информации о файлах в облаке (Яндекс Диск).
 * Используется для быстрой проверки изменений и дедупликации по SHA-1 без скачивания файлов.
 */
@Entity(tableName = "cloud_file_cache")
data class CloudFileEntity(
    @PrimaryKey val path: String,       // Полный путь файла на Яндекс Диске (например, "/Books/MyBook.fb2")
    val sha1: String,                   // SHA-1 хэш содержимого файла
    val size: Long,                     // Размер файла в байтах
    val lastModified: String            // Дата последнего изменения от API Яндекса
)

/**
 * DAO для управления кэшем облачных файлов.
 */
@Dao
interface CloudFileDao {

    @Query("SELECT * FROM cloud_file_cache WHERE path = :path LIMIT 1")
    suspend fun getByPath(path: String): CloudFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CloudFileEntity)

    @Query("SELECT * FROM cloud_file_cache")
    suspend fun getAll(): List<CloudFileEntity>

    @Query("DELETE FROM cloud_file_cache")
    suspend fun clearAll()

    @Query("DELETE FROM cloud_file_cache WHERE path = :path")
    suspend fun deleteByPath(path: String)
}
