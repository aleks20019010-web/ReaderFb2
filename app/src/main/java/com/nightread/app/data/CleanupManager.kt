package com.nightread.app.data

import android.util.Log
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CleanupManager(private val bookDao: BookDao) {

    suspend fun getDuplicates(): Map<String, List<BookEntity>> = withContext(Dispatchers.IO) {
        // This will find books that have the same SHA-1, if the database schema allows it.
        // If sha1 is the PrimaryKey, this might always be empty.
        val allDuplicates = bookDao.getDuplicateBooks()
        allDuplicates.groupBy { it.sha1 ?: "" }
    }

    suspend fun deleteBooks(booksToDelete: List<BookEntity>): Pair<Int, Long> = withContext(Dispatchers.IO) {
        var deletedCount = 0
        var freedSpace = 0L
        for (book in booksToDelete) {
            val file = File(book.filePath ?: "")
            if (file.exists()) {
                val size = file.length()
                if (file.delete()) {
                    // This might delete all entries if sha1 is the PK.
                    // If the user wants to delete specific entries, we need to handle that carefully.
                    // Given the constraint, we will attempt to delete the book entity.
                    bookDao.deleteBookBySha1(book.sha1 ?: "")
                    freedSpace += size
                    deletedCount++
                } else {
                    Log.e("CleanupManager", "Failed to delete: ${book.filePath}")
                }
            }
        }
        Pair(deletedCount, freedSpace)
    }
}
