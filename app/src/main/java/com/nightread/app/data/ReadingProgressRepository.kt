package com.nightread.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ReadingProgress(
    val bookId: String,
    val sourceOffset: Int,
    val updatedAt: Long
)

interface ReadingProgressRepository {
    suspend fun getProgress(bookId: String): ReadingProgress?
    suspend fun saveProgress(progress: ReadingProgress)
    suspend fun deleteProgress(bookId: String)
}

class RoomReadingProgressRepository(private val bookDao: BookDao) : ReadingProgressRepository {
    
    override suspend fun getProgress(bookId: String): ReadingProgress? = withContext(Dispatchers.IO) {
        val book = bookDao.getBookBySha1(bookId)
        if (book != null && book.currentProgressChar >= 0) {
            ReadingProgress(
                bookId = bookId,
                sourceOffset = book.currentProgressChar,
                updatedAt = book.lastReadTime
            )
        } else {
            null
        }
    }

    override suspend fun saveProgress(progress: ReadingProgress) = withContext(Dispatchers.IO) {
        // Find existing to keep properties
        val book = bookDao.getBookBySha1(progress.bookId)
        if (book != null) {
            bookDao.updateProgress(
                sha1 = progress.bookId,
                charOffset = progress.sourceOffset,
                timestamp = progress.updatedAt
            )
        }
    }

    override suspend fun deleteProgress(bookId: String) = withContext(Dispatchers.IO) {
        bookDao.updateProgress(
            sha1 = bookId,
            charOffset = 0,
            timestamp = System.currentTimeMillis()
        )
    }
}
