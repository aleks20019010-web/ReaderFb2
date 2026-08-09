package com.nightread.app

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.junit.Assert.*
import kotlinx.coroutines.runBlocking
import com.nightread.app.ui.customlayout.ReaderSearchEngine
import com.nightread.app.ui.customlayout.ReaderDocument
import com.nightread.app.ui.customlayout.ReaderChapter
import com.nightread.app.ui.customlayout.ReaderChunk
import com.nightread.app.ui.customlayout.ReaderPager
import com.nightread.app.ui.customlayout.ReaderConfiguration
import com.nightread.app.ui.customlayout.ReaderViewport
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

@RunWith(RobolectricTestRunner::class)
class ReaderIntegrationAuditTest {

    @Test
    fun testSearch_SourceOffsetInvariants() = runBlocking {
        val text = "Chapter 1. This is some text. [FINDME] Chapter 2. More text here."
        val engine = ReaderSearchEngine(text)
        val query = "[FINDME]"
        
        val results = engine.search(query)
        assertEquals(1, results.size)
        val result = results[0]
        
        // Invariant: sourceStartOffset must point exactly to the match in original text
        assertEquals(query, text.substring(result.sourceStartOffset, result.sourceEndOffset))
        
        // Check context
        assertTrue(result.contextBefore.contains("text"))
        assertTrue(result.contextAfter.contains("Chapter 2"))
    }

    @Test
    fun testSearch_CrossChapterBoundary() = runBlocking {
        // Search should not care about chapters because it runs on the full text
        val text = "End of Chapter 1. MatchThis. Start of Chapter 2."
        val engine = ReaderSearchEngine(text)
        val results = engine.search("MatchThis")
        
        assertEquals(1, results.size)
        assertEquals("MatchThis", text.substring(results[0].sourceStartOffset, results[0].sourceEndOffset))
    }

    @Test
    fun testBookmark_UsesSourceOffset() {
        // Bookmarks should store charOffset as the source of truth
        val bookSha1 = "test_sha1"
        val offset = 1234
        val pageIndex = 10 // unstable, but stored for UI
        
        val bookmark = com.nightread.app.data.BookmarkEntity(
            bookSha1 = bookSha1,
            bookTitle = "Title",
            charOffset = offset,
            pageIndex = pageIndex,
            snippet = "Some snippet"
        )
        
        assertEquals(offset, bookmark.charOffset)
    }

    @Test
    fun testSearch_EmptyQuery() = runBlocking {
        val engine = ReaderSearchEngine("Some text")
        val results = engine.search("")
        assertTrue(results.isEmpty())
    }

    @Test
    fun testSearch_NoResults() = runBlocking {
        val engine = ReaderSearchEngine("Some text")
        val results = engine.search("NotFound")
        assertTrue(results.isEmpty())
    }
}
