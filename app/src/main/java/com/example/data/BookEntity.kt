package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val author: String,
    val content: String,
    val coverGradientStart: String = "#FF6B6B",
    val coverGradientEnd: String = "#4D96FF",
    val category: String = "Классика",
    val currentProgressChar: Int = 0,
    val totalCharacters: Int = 0,
    val lastReadTime: Long = System.currentTimeMillis()
)
