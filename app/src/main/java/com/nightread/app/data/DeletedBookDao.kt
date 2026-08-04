package com.nightread.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DeletedBookDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeletedBook(deletedBook: DeletedBook)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeletedBooks(deletedBooks: List<DeletedBook>)

    @Query("SELECT sha1 FROM deleted_books")
    suspend fun getAllDeletedSha1s(): List<String>

    @Query("SELECT * FROM deleted_books")
    suspend fun getAllDeletedBooks(): List<DeletedBook>

    @Query("SELECT EXISTS(SELECT 1 FROM deleted_books WHERE sha1 = :sha1 OR (filePath IS NOT NULL AND filePath = :filePath))")
    suspend fun isBookDeleted(sha1: String, filePath: String?): Boolean

    @Query("DELETE FROM deleted_books WHERE sha1 = :sha1 OR (filePath IS NOT NULL AND filePath = :filePath)")
    suspend fun removeDeletedBook(sha1: String, filePath: String?)

    @Query("DELETE FROM deleted_books")
    suspend fun deleteAllDeletedBooks()
}
