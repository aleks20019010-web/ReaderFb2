package com.nightread.app.domain

import com.nightread.app.data.BookmarkEntity
import com.nightread.app.data.BookmarkRepository
import kotlinx.coroutines.flow.Flow

class ManageBookmarksUseCase(private val bookmarkRepository: BookmarkRepository) {

    fun getBookmarksForBook(bookSha1: String): Flow<List<BookmarkEntity>> {
        return bookmarkRepository.getBookmarksForBook(bookSha1)
    }

    suspend fun addBookmark(bookmark: BookmarkEntity): Long {
        return bookmarkRepository.insertBookmark(bookmark)
    }

    suspend fun deleteBookmark(bookmark: BookmarkEntity) {
        bookmarkRepository.deleteBookmark(bookmark)
    }

    suspend fun deleteBookmarkById(id: Int) {
        bookmarkRepository.deleteBookmarkById(id)
    }
}
