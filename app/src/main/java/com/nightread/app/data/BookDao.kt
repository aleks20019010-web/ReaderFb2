package com.nightread.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class Sha1PathTuple(
    val sha1: String,
    val filePath: String
)

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastReadTime DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE title LIKE :query OR author LIKE :query ORDER BY lastReadTime DESC")
    fun searchBooks(query: String): Flow<List<BookEntity>>

    @Query("SELECT sha1 FROM books")
    suspend fun getAllSha1s(): List<String>

    @Query("SELECT sha1, filePath FROM books")
    suspend fun getSha1ToPathMap(): List<Sha1PathTuple>

    @Query("UPDATE books SET filePath = :newPath WHERE sha1 = :sha1")
    suspend fun updateFilePath(sha1: String, newPath: String)

    @Query("SELECT * FROM books WHERE sha1 = :sha1 LIMIT 1")
    suspend fun getBookBySha1(sha1: String): BookEntity?

    @Query("SELECT * FROM books WHERE sha1 = :sha1")
    suspend fun getBooksBySha1(sha1: String): List<BookEntity>

    @androidx.room.Transaction
    suspend fun insertBookIfUnique(book: BookEntity): Boolean {
        val existing = getBookBySha1(book.sha1)
        return if (existing == null) {
            insertBook(book)
            true
        } else {
            false
        }
    }

    @Query("SELECT * FROM books WHERE author = :author ORDER BY title ASC")
    fun getBooksByAuthor(author: String): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE series = :series ORDER BY seriesIndex ASC, title ASC")
    fun getBooksBySeries(series: String): Flow<List<BookEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooks(books: List<BookEntity>)

    @Update
    suspend fun updateBook(book: BookEntity)

    @Query("UPDATE books SET currentProgressChar = :charOffset, lastReadTime = :timestamp WHERE sha1 = :sha1")
    suspend fun updateProgress(sha1: String, charOffset: Int, timestamp: Long)

    @Query("UPDATE books SET currentProgressChar = :charOffset, currentPageIndex = :pageIndex, lastReadTime = :timestamp WHERE sha1 = :sha1")
    suspend fun updateProgressAndPage(sha1: String, charOffset: Int, pageIndex: Int, timestamp: Long)

    @Query("DELETE FROM books WHERE sha1 = :sha1")
    suspend fun deleteBookBySha1(sha1: String)

    @Query("DELETE FROM books")
    suspend fun deleteAllBooks()
}
