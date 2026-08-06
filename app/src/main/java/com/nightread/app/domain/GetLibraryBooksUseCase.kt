package com.nightread.app.domain

import com.nightread.app.data.BookEntity
import com.nightread.app.data.BookRepository
import com.nightread.app.data.SettingsManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetLibraryBooksUseCase(private val bookRepository: BookRepository) {

    operator fun invoke(query: String = "", sortOption: String = SettingsManager.SORT_DATE_DESC): Flow<List<BookEntity>> {
        val flow = if (query.isBlank()) {
            bookRepository.allBooks
        } else {
            bookRepository.searchBooks(query)
        }

        return flow.map { books ->
            when (sortOption) {
                SettingsManager.SORT_TITLE_ASC -> books.sortedBy { it.title.lowercase() }
                SettingsManager.SORT_TITLE_DESC -> books.sortedByDescending { it.title.lowercase() }
                SettingsManager.SORT_AUTHOR_ASC -> books.sortedBy { (it.author ?: "").lowercase() }
                SettingsManager.SORT_AUTHOR_DESC -> books.sortedByDescending { (it.author ?: "").lowercase() }
                SettingsManager.SORT_PROGRESS_ASC -> books.sortedBy { if (it.totalCharacters > 0) it.currentProgressChar.toFloat() / it.totalCharacters else 0f }
                SettingsManager.SORT_PROGRESS_DESC -> books.sortedByDescending { if (it.totalCharacters > 0) it.currentProgressChar.toFloat() / it.totalCharacters else 0f }
                SettingsManager.SORT_DATE_ASC -> books.sortedBy { it.lastReadTime }
                else -> books.sortedByDescending { it.lastReadTime }
            }
        }
    }
}
