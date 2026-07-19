package com.nightread.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT DISTINCT bookSha1 FROM bookmarks")
    fun getBookSha1sWithBookmarks(): Flow<List<String>>

    @Query("SELECT * FROM bookmarks ORDER BY timestamp DESC")
    fun getAllBookmarks(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE bookSha1 = :bookSha1 ORDER BY charOffset ASC")
    fun getBookmarksForBook(bookSha1: String): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE bookSha1 = :bookSha1 AND charOffset = :charOffset LIMIT 1")
    suspend fun getBookmarkAtOffset(bookSha1: String, charOffset: Int): BookmarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmarkById(id: Int)

    @Query("DELETE FROM bookmarks WHERE bookSha1 = :bookSha1 AND charOffset = :charOffset")
    suspend fun deleteBookmarkAtOffset(bookSha1: String, charOffset: Int)
}
