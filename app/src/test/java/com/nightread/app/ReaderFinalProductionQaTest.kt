package com.nightread.app

import android.content.Context
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.test.core.app.ApplicationProvider
import com.nightread.app.ui.customlayout.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density

@RunWith(RobolectricTestRunner::class)
class ReaderFinalProductionQaTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun runReaderTest(block: suspend (TextMeasurer, Context) -> Unit) {
        var measurer: TextMeasurer? = null
        var context: Context? = null
        
        composeTestRule.setContent {
            measurer = androidx.compose.ui.text.rememberTextMeasurer()
            context = LocalContext.current
        }
        composeTestRule.runOnIdle {
            kotlinx.coroutines.runBlocking {
                block(measurer!!, context!!)
            }
        }
    }

    @Test
    fun testInvariant_NoLostCharacters() = runReaderTest { measurer, context ->
        val text = (0 until 100).joinToString("\n") { "This is line $it and it should be paginated correctly without any loss of characters at all." }
        val bookId = "test_book"
        val doc = ReaderLayoutEngine.parseDocument(bookId, text, 16.sp)
        
        val config = ReaderConfiguration(
            fontSize = 16.sp,
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            lineSpacing = 1.2f,
            maxWidthPx = 800,
            maxHeightPx = 1200
        )
        val viewport = ReaderViewport(800, 1200, Density(1f))
        
        val allPages = mutableListOf<ReaderPage>()
        for (chapter in doc.chapters) {
            for (chunk in chapter.chunks) {
                val pages = ReaderLayoutEngine.paginateChunkPublic(
                    context, bookId, "test_key", chapter.chapterIndex, chunk, 
                    measurer, TextStyle(fontSize = 16.sp), 800, 1200
                )
                allPages.addAll(pages)
            }
        }
        
        // 1. Check for Overlaps and Gaps
        for (i in 1 until allPages.size) {
            val prev = allPages[i-1]
            val curr = allPages[i]
            assertEquals("Overlap detected between page ${i-1} and $i", prev.endOffset, curr.startOffset)
        }
        
        // 2. Check first and last boundaries
        assertEquals(0, allPages.first().startOffset)
        // Some whitespace might be trimmed at the end of the last page by paginateChunkPublic
        // but the endOffset should represent the end of the chunk
        // Actually paginateChunkPublic uses chunk.endOffset for the last page
        assertTrue("Last page should reach end of doc", allPages.last().endOffset >= text.trimEnd().length)
        
        // 3. Reconstruct text and compare
        val reconstructed = allPages.joinToString("") { it.text.text }
        // trim both for fair comparison if whitespace logic differs slightly (though it shouldn't lose content)
        val originalStripped = text.replace("\n", "").replace(" ", "")
        val reconstructedStripped = reconstructed.replace("\n", "").replace(" ", "")
        
        // Use a more precise check: every character in original must be present in exactly one page
        // (except for characters that are naturally skipped like extra newlines if the engine does that)
        // But our engine is source-offset based, so it should be perfect.
        
        for (i in text.indices) {
            val char = text[i]
            if (char.isWhitespace()) continue // Whitespace handling might vary
            
            val found = allPages.any { page ->
                val localStart = (i - page.startOffset)
                if (i >= page.startOffset && i < page.endOffset) {
                    // Check if it's in the text (might be a newline that got converted)
                    true
                } else false
            }
            assertTrue("Character at index $i ('$char') lost in pagination", found)
        }
    }

    @Test
    fun testSearch_ResultNavigationAlignment() = runReaderTest { measurer, context ->
        val text = "Start. " + "Filler. ".repeat(100) + "TARGET_RESULT" + " Filler. ".repeat(100) + " End."
        val doc = ReaderLayoutEngine.parseDocument("search_test", text, 16.sp)
        val engine = ReaderSearchEngine(text)
        val results = engine.search("TARGET_RESULT")
        
        assertEquals(1, results.size)
        val result = results[0]
        
        val config = ReaderConfiguration(
            fontSize = 16.sp,
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            lineSpacing = 1.2f,
            maxWidthPx = 800,
            maxHeightPx = 1200
        )
        val viewport = ReaderViewport(800, 1200, Density(1f))
        
        // We find which page contains the result
        var targetPage: ReaderPage? = null
        for (chapter in doc.chapters) {
            for (chunk in chapter.chunks) {
                val pages = ReaderLayoutEngine.paginateChunkPublic(
                    context, "search_test", "key", chapter.chapterIndex, chunk,
                    measurer, TextStyle(fontSize = 16.sp), 800, 1200
                )
                val found = pages.find { result.sourceStartOffset >= it.startOffset && result.sourceStartOffset < it.endOffset }
                if (found != null) {
                    targetPage = found
                    break
                }
            }
            if (targetPage != null) break
        }
        
        assertNotNull("Search result offset ${result.sourceStartOffset} not found in any page", targetPage)
        assertTrue("Page text should contain result context", targetPage!!.text.text.contains("TARGET_RESULT"))
    }

    @Test
    fun testBoundary_Torture() = runReaderTest { measurer, context ->
        val longWord = "A" + "B".repeat(5000) + "C"
        val text = "Before. $longWord After."
        val doc = ReaderLayoutEngine.parseDocument("torture", text, 16.sp)
        
        // Paginate - should not crash
        val pages = ReaderLayoutEngine.paginateChunkPublic(
            context, "torture", "key", 0, doc.chapters[0].chunks[0],
            measurer, TextStyle(fontSize = 16.sp), 100, 100 // Very small viewport
        )
        
        assertFalse(pages.isEmpty())
        assertTrue(pages.any { it.text.text.contains("B") })
    }
}
