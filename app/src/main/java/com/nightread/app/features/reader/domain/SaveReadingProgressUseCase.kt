package com.nightread.app.features.reader.domain

import com.nightread.app.data.BookRepository

class SaveReadingProgressUseCase(private val bookRepository: BookRepository) {

    suspend operator fun invoke(bookSha1: String, progressChar: Int, totalChars: Int) {
        val book = bookRepository.getBookBySha1(bookSha1) ?: return
        val updated = book.copy(
            currentProgressChar = progressChar,
            totalCharacters = totalChars,
            lastReadTime = System.currentTimeMillis()
        )
        bookRepository.updateBook(updated)
    }
}
