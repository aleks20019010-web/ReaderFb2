package com.nightread.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ScannerCacheDao {
    @Query("SELECT * FROM scanner_cache")
    suspend fun getAll(): List<ScannerCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ScannerCacheEntity>)

    @Query("DELETE FROM scanner_cache")
    suspend fun clear()
}
