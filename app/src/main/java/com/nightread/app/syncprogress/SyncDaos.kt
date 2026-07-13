package com.nightread.app.syncprogress

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncBookDao {
    @Query("SELECT * FROM sync_books_progress")
    fun getAllBooks(): Flow<List<Book>>

    @Query("SELECT * FROM sync_books_progress WHERE sha1 = :sha1 LIMIT 1")
    suspend fun findBySha1(sha1: String): Book?

    @Query("SELECT * FROM sync_books_progress WHERE isDeleted = 0")
    fun findAllActive(): Flow<List<Book>>

    @Query("SELECT * FROM sync_books_progress WHERE isDeleted = 0")
    suspend fun findAllActiveSync(): List<Book>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(book: Book)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(books: List<Book>)

    @Delete
    suspend fun delete(book: Book)

    @Query("DELETE FROM sync_books_progress")
    suspend fun deleteAll()
}

@Dao
interface SyncReadingProgressDao {
    @Query("SELECT * FROM sync_reading_progress")
    fun getAllProgressFlow(): Flow<List<ReadingProgress>>

    @Query("SELECT * FROM sync_reading_progress WHERE bookId = :bookId LIMIT 1")
    suspend fun getProgressForBook(bookId: String): ReadingProgress?

    @Query("SELECT * FROM sync_reading_progress")
    suspend fun getAllProgressSync(): List<ReadingProgress>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgress(progress: ReadingProgress)

    @Delete
    suspend fun deleteProgress(progress: ReadingProgress)

    @Query("DELETE FROM sync_reading_progress")
    suspend fun deleteAllProgress()
}

@Dao
interface SyncSha1CacheDao {
    @Query("SELECT * FROM sync_sha1_cache WHERE accountId = :accountId AND filePath = :filePath LIMIT 1")
    suspend fun getCache(accountId: String, filePath: String): Sha1Cache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCache(cache: Sha1Cache)

    @Query("DELETE FROM sync_sha1_cache WHERE accountId = :accountId AND filePath = :filePath")
    suspend fun deleteCache(accountId: String, filePath: String)

    @Query("DELETE FROM sync_sha1_cache WHERE accountId = :accountId")
    suspend fun deleteByAccount(accountId: String)

    @Query("DELETE FROM sync_sha1_cache WHERE cachedAt < :thresholdTime")
    suspend fun deleteExpired(thresholdTime: Long)

    @Query("DELETE FROM sync_sha1_cache")
    suspend fun deleteAll()
}
