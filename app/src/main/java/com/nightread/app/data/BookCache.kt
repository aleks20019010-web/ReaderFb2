package com.nightread.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "book_cache")
data class BookCache(
    @PrimaryKey val path: String,          // полный путь к файлу
    val fingerprint: String,                // композитный ключ
    val textHash: String?,                  // SHA256 текста (если извлечён)
    val author: String,                     // нормализованный автор
    val title: String,                      // нормализованное название
    val fileSize: Long,                     // размер файла
    val lastScanned: Long,                  // timestamp сканирования
    val format: String                      // fb2, epub, mobi и т.д.
)
