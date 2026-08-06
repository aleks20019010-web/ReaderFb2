package com.nightread.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val sha1: String,
    val title: String,
    val author: String? = "Неизвестен",
    val coverGradientStart: String = "#FF6B6B",
    val coverGradientEnd: String = "#4D96FF",
    val category: String = "Классика",
    val currentProgressChar: Int = 0,
    val totalCharacters: Int = 0,
    val lastReadTime: Long = 0L,
    val filePath: String? = null,
    val series: String? = null,
    val language: String? = "ru",
    val fileSize: Long = 0L,
    val review: String? = null,
    val isFavorite: Boolean = false,
    val coverPath: String? = null,
    val seriesIndex: Int? = null,
    val annotation: String? = null,
    val currentPageIndex: Int = 0,
    val isNew: Boolean = false,
    val summary: String? = null,
    val characters: String? = null,
    val chapterDescriptions: String? = null,
    val tags: String? = null,
    val isWantToRead: Boolean = false,
    val dateAdded: Long = System.currentTimeMillis()
)

fun getRandomGradientStartColor(): String {
    return listOf("#1A1A2E", "#16213E", "#0F3460", "#3B0066").random()
}

fun getRandomGradientEndColor(): String {
    return listOf("#E94560", "#00ADB5", "#FF2E63", "#FF9F43").random()
}

