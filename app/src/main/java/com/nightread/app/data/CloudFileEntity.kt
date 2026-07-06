package com.nightread.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cloud_file_cache")
data class CloudFileEntity(
    @PrimaryKey val path: String,
    val sha1: String,
    val size: Long,
    val lastModified: String
)
