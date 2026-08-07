package com.nightread.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface BookCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookCache: BookCache)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(bookCaches: List<BookCache>)

    @Update
    suspend fun update(bookCache: BookCache)

    @Query("DELETE FROM book_cache WHERE path = :path")
    suspend fun deleteByPath(path: String)

    @Query("DELETE FROM book_cache WHERE path IN (:paths)")
    suspend fun deleteByPaths(paths: List<String>)

    @Query("SELECT * FROM book_cache WHERE path = :path LIMIT 1")
    suspend fun getByPath(path: String): BookCache?

    @Query("SELECT * FROM book_cache")
    suspend fun getAll(): List<BookCache>

    @Query("SELECT path FROM book_cache")
    suspend fun getAllPaths(): List<String>

    @Query("SELECT * FROM book_cache WHERE fingerprint = :fingerprint")
    suspend fun getByFingerprint(fingerprint: String): List<BookCache>

    @Query("SELECT * FROM book_cache WHERE fingerprint IN (SELECT fingerprint FROM book_cache GROUP BY fingerprint HAVING COUNT(*) > 1)")
    suspend fun getDuplicateEntries(): List<BookCache>

    @Query("SELECT * FROM book_cache WHERE path IN (:paths)")
    suspend fun getByPaths(paths: List<String>): List<BookCache>

    @Query("DELETE FROM book_cache")
    suspend fun deleteAll()
}
