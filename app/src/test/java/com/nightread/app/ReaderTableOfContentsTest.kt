package com.nightread.app

import com.nightread.app.ui.customlayout.ReaderLayoutEngine
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ReaderTableOfContentsTest {

    @Test
    fun testTOC_ManyChapters() = runBlocking {
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
    fun testTOC_CurrentChapterDetection() = runBlocking {
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
    fun testTOC_EmptyBook() = runBlocking {
        val text = ""
        val doc = ReaderLayoutEngine.parseDocument("empty", text, 16.sp)
        assertTrue(doc.chapters.isEmpty())
    }

    @Test
    fun testTOC_SingleChapter() = runBlocking {
        val text = "Only text, no chapters."
        val doc = ReaderLayoutEngine.parseDocument("single", text, 16.sp)
        assertEquals(1, doc.chapters.size)
        assertEquals("Начало книги", doc.chapters[0].title)
    }
}
