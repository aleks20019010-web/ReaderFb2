package com.nightread.app

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.junit.Assert.*
import kotlinx.coroutines.runBlocking
import com.nightread.app.data.BookmarkEntity
import com.nightread.app.data.BookmarkRepository
import com.nightread.app.data.BookmarkDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room

@RunWith(RobolectricTestRunner::class)
class BookmarkIntegrationTest {

    @Test
    fun testBookmarkOperations() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, BookmarkDatabase::class.java).allowMainThreadQueries().build()
        val repo = BookmarkRepository(db.bookmarkDao())
        
        val bookSha1 = "test_book_1"
        
        // 1. ADD BOOKMARK
        val b1 = BookmarkEntity(bookSha1 = bookSha1, bookTitle = "Title", charOffset = 100, pageIndex = 1, snippet = "Text 100")
        repo.insertBookmark(b1)
        
        // 2. BOOKMARK PERSISTENCE & RESTORE
        val bookmarks = repo.getBookmarkAtOffset(bookSha1, 100)
        assertNotNull(bookmarks)
        assertEquals("Text 100", bookmarks?.snippet)
        
        // MULTIPLE BOOKMARKS
        val b2 = BookmarkEntity(bookSha1 = bookSha1, bookTitle = "Title", charOffset = 500, pageIndex = 5, snippet = "Text 500")
        repo.insertBookmark(b2)
        
        // check flow if possible, or just the DB query
        val list = db.bookmarkDao().getAllBookmarks()
        // getAllBookmarks returns Flow, so it's harder to test synchronously without turbine.
        // We'll just verify the specific bookmark
        val b2Restored = repo.getBookmarkAtOffset(bookSha1, 500)
        assertNotNull(b2Restored)
        
        // 3. REMOVE BOOKMARK
        repo.deleteBookmarkAtOffset(bookSha1, 100)
        val b1AfterDelete = repo.getBookmarkAtOffset(bookSha1, 100)
        assertNull(b1AfterDelete)
    }
}
