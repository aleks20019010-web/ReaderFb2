package com.nightread.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scanner_cache")
data class ScannerCacheEntity(
    @PrimaryKey val path: String,
    val lastModified: Long,
    val fileSize: Long,
    val sha1: String
)
