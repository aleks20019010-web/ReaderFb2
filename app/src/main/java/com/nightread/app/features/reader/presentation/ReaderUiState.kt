package com.nightread.app.features.reader.presentation

import com.nightread.app.data.BookEntity
import com.nightread.app.data.BookmarkEntity
import com.nightread.app.data.NoteEntity

sealed interface ReaderUiState {
    object Loading : ReaderUiState
    
    data class Success(
        val book: BookEntity,
        val contentPages: List<String> = emptyList(),
        val currentPageIndex: Int = 0,
        val totalPages: Int = 0,
        val bookmarks: List<BookmarkEntity> = emptyList(),
        val notes: List<NoteEntity> = emptyList(),
        val fontSizeSp: Float = 18f,
        val lineSpacing: Float = 1.4f,
        val themeMode: String = "light",
        val isTtsPlaying: Boolean = false
    ) : ReaderUiState

    data class Error(val message: String) : ReaderUiState
}
