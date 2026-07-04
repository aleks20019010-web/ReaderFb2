package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastReadTime DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookById(id: Int): BookEntity?

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

    @Query("UPDATE books SET currentProgressChar = :charOffset, lastReadTime = :timestamp WHERE id = :id")
    suspend fun updateProgress(id: Int, charOffset: Int, timestamp: Long)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBookById(id: Int)
}
