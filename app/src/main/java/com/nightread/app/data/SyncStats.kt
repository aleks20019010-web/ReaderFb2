package com.nightread.app.data

/**
 * Статистика синхронизации, вычисляемая перед запуском копирования/загрузки.
 */
data class SyncStats(
    val booksOnDisk: Int,                      // Общее число книг на Яндекс Диске
    val booksLocal: Int,                       // Общее число книг локально на устройстве
    val toDownload: List<CloudFileEntity>,     // Список книг в облаке, которых нет локально (по SHA-1)
    val toUpload: List<BookEntity>,            // Список локальных книг, которых нет в облаке (по SHA-1 и имени)
    val duplicates: Int,                       // Количество пропущенных (уже существующих) книг
    val cloudProgressItems: List<ResourceItem> // Файлы прогресса чтения на диске
)
