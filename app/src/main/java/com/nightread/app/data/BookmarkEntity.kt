package com.nightread.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bookSha1: String,
    val bookTitle: String,
    val charOffset: Int,
    val pageIndex: Int,
    val snippet: String,
    val timestamp: Long = System.currentTimeMillis()
)
