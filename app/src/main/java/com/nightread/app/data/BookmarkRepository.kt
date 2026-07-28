package com.nightread.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class BookmarkRepository(private val bookmarkDao: BookmarkDao) {

    val allBookmarks: Flow<List<BookmarkEntity>> = bookmarkDao.getAllBookmarks()

    val bookSha1sWithBookmarks: Flow<List<String>> = bookmarkDao.getBookSha1sWithBookmarks()

    fun getBookmarksForBook(bookSha1: String): Flow<List<BookmarkEntity>> {
        return bookmarkDao.getBookmarksForBook(bookSha1)
    }

    suspend fun getBookmarkAtOffset(bookSha1: String, charOffset: Int): BookmarkEntity? {
        return withContext(Dispatchers.IO) {
            bookmarkDao.getBookmarkAtOffset(bookSha1, charOffset)
        }
    }

    suspend fun insertBookmark(bookmark: BookmarkEntity): Long {
        return withContext(Dispatchers.IO) {
            bookmarkDao.insertBookmark(bookmark)
        }
    }

    suspend fun deleteBookmark(bookmark: BookmarkEntity) {
        withContext(Dispatchers.IO) {
            bookmarkDao.deleteBookmark(bookmark)
        }
    }

    suspend fun deleteBookmarkById(id: Int) {
        withContext(Dispatchers.IO) {
            bookmarkDao.deleteBookmarkById(id)
        }
    }

    suspend fun deleteBookmarkAtOffset(bookSha1: String, charOffset: Int) {
        withContext(Dispatchers.IO) {
            bookmarkDao.deleteBookmarkAtOffset(bookSha1, charOffset)
        }
    }
}
