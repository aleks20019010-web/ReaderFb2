package com.nightread.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CloudFileDao {
    @Query("SELECT * FROM cloud_file_cache WHERE path = :path")
    suspend fun getByPath(path: String): CloudFileEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CloudFileEntity)
    
    @Query("SELECT * FROM cloud_file_cache")
    suspend fun getAll(): List<CloudFileEntity>
}
