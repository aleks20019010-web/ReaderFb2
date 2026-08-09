package com.nightread.app

import android.content.Context
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.nightread.app.ui.customlayout.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density

@RunWith(RobolectricTestRunner::class)
class ReaderTableOfContentsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun runReaderTest(block: suspend (TextMeasurer, Context) -> Unit) = runBlocking {
        var measurer: TextMeasurer? = null
        var context: Context? = null
        
        composeTestRule.setContent {
            measurer = androidx.compose.ui.text.rememberTextMeasurer()
            context = LocalContext.current
        }
        composeTestRule.waitForIdle()
        
        block(measurer!!, context!!)
    }

    @Test
    fun testTOC_ManyChapters() = runReaderTest { measurer, context ->
        val text = (0 until 50).joinToString("\n\n") { "[CHAPTER]Chapter $it[/CHAPTER]\nContent for chapter $it." }
        val doc = ReaderLayoutEngine.parseDocument("toc_test", text, 16.sp)
        
        assertEquals(50, doc.chapters.size)
        
        // Check offsets
        for (i in 0 until 50) {
            val chapter = doc.chapters[i]
            assertEquals("Chapter $i", chapter.title)
        }
    }

    @Test
    fun testTOC_CurrentChapterDetection() = runReaderTest { measurer, context ->
        val text = "[CHAPTER]Ch1[/CHAPTER]\nContent1\n[CHAPTER]Ch2[/CHAPTER]\nContent2"
        val doc = ReaderLayoutEngine.parseDocument("toc_curr", text, 16.sp)
        
        val ch1 = doc.chapters[0]
        val ch2 = doc.chapters[1]
        
        // Simulate finding current chapter at different offsets
        fun findChapter(offset: Int) = doc.chapters.find { it.startOffset <= offset && it.endOffset > offset }
        
        assertEquals(ch1, findChapter(ch1.startOffset))
        assertEquals(ch1, findChapter(ch1.startOffset + 5))
        assertEquals(ch2, findChapter(ch2.startOffset))
        assertEquals(ch2, findChapter(ch2.startOffset + 5))
    }

    @Test
    fun testTOC_EmptyBook() = runReaderTest { measurer, context ->
        val text = ""
        val doc = ReaderLayoutEngine.parseDocument("empty", text, 16.sp)
        assertFalse(doc.chapters.isEmpty())
        assertEquals("Начало книги", doc.chapters[0].title)
    }

    @Test
    fun testTOC_SingleChapter() = runReaderTest { measurer, context ->
        val text = "Only text, no chapters."
        val doc = ReaderLayoutEngine.parseDocument("single", text, 16.sp)
        assertEquals(1, doc.chapters.size)
        assertEquals("Начало книги", doc.chapters[0].title)
    }
}
