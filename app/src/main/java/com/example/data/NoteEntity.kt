package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bookId: Int,
    val bookTitle: String,
    val selectedText: String,
    val noteText: String,
    val charOffset: Int,
    val timestamp: Long = System.currentTimeMillis()
)
