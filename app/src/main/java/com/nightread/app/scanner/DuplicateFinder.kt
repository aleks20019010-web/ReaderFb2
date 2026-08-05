package com.nightread.app.scanner

import com.nightread.app.data.BookCache
import com.nightread.app.data.BookCacheDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DuplicateFinder(private val bookCacheDao: BookCacheDao) {

    /**
     * Поиск дубликатов книг в Room БД.
     * Группирует записи по fingerprint и возвращает только те группы, где размер списка > 1.
     * Возвращает карту: Fingerprint -> Список дублирующихся файлов.
     */
    suspend fun findDuplicates(): Map<String, List<BookCache>> = withContext(Dispatchers.IO) {
        val duplicateEntries = bookCacheDao.getDuplicateEntries()
        duplicateEntries
            .groupBy { it.fingerprint }
            .filterValues { it.size > 1 }
    }

    /**
     * Поиск дубликатов из произвольного передаваемого списка объектов BookCache.
     */
    fun findDuplicatesFromList(books: List<BookCache>): Map<String, List<BookCache>> {
        return books
            .groupBy { it.fingerprint }
            .filterValues { it.size > 1 }
    }
}
