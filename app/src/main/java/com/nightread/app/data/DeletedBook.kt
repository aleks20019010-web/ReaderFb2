package com.nightread.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "deleted_books")
data class DeletedBook(
    @PrimaryKey val sha1: String,
    val filePath: String? = null,
    val deletedAt: Long = System.currentTimeMillis()
)
