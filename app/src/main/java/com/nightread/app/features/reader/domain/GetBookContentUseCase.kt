package com.nightread.app.features.reader.domain

import com.nightread.app.data.BookEntity
import com.nightread.app.data.BookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed class BookContentResult {
    data class Success(
        val content: String,
        val isHtml: Boolean,
        val title: String,
        val author: String?
    ) : BookContentResult()

    data class Error(val message: String) : BookContentResult()
}

class GetBookContentUseCase(private val bookRepository: BookRepository) {

    suspend operator fun invoke(bookSha1: String): BookContentResult = withContext(Dispatchers.IO) {
        try {
            val book = bookRepository.getBookBySha1(bookSha1)
                ?: return@withContext BookContentResult.Error("Book not found")

            val path = book.filePath
            if (path.isNullOrEmpty()) {
                return@withContext BookContentResult.Error("File path is null or empty")
            }
            val cleanPath = if (path.startsWith("file://")) path.removePrefix("file://") else path
            val file = File(cleanPath)
            if (!file.exists()) {
                return@withContext BookContentResult.Error("File does not exist: ${book.filePath}")
            }

            when (file.extension.lowercase()) {
                "txt" -> {
                    val text = file.readText(Charsets.UTF_8)
                    BookContentResult.Success(text, false, book.title, book.author)
                }
                else -> {
                    BookContentResult.Success(book.annotation ?: "", false, book.title, book.author)
                }
            }
        } catch (e: Exception) {
            BookContentResult.Error(e.message ?: "Failed to read book content")
        }
    }
}
