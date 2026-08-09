package com.nightread.app

import com.nightread.app.ui.customlayout.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import androidx.compose.ui.unit.sp

@RunWith(RobolectricTestRunner::class)
class ReaderLayoutEngineTest {

    @Test
    fun testParseDocumentAndChapters() {
        val text = "Глава 1\nПривет мир!\n\nГлава 2\nВторой параграф."
        val doc = kotlinx.coroutines.runBlocking {
            ReaderLayoutEngine.parseDocument("book_1", text, 16.sp)
        }

        assertNotNull(doc)
        assertEquals("book_1", doc.bookId)
        assertTrue(doc.chapters.isNotEmpty())
        assertTrue(doc.paragraphs.isNotEmpty())
    }

    @Test
    fun testValidationPassesOnValidPages() {
        val pages = listOf(
            ReaderPage(0, androidx.compose.ui.text.AnnotatedString("Page 0"), 0, 10),
            ReaderPage(1, androidx.compose.ui.text.AnnotatedString("Page 1"), 10, 20),
            ReaderPage(2, androidx.compose.ui.text.AnnotatedString("Page 2"), 20, 30)
        )
        val result = ReaderLayoutEngine.validatePagination(pages, 1000)
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun testValidationDetectsOverlap() {
        val pages = listOf(
            ReaderPage(0, androidx.compose.ui.text.AnnotatedString("Page 0"), 0, 15),
            ReaderPage(1, androidx.compose.ui.text.AnnotatedString("Page 1"), 10, 25)
        )
        val result = ReaderLayoutEngine.validatePagination(pages, 1000)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("OVERLAP") })
    }

    @Test
    fun testPageIndexBinarySearch() {
        val entries = listOf(
            ReaderLayoutEngine.PageIndexEntry(0, 0, 0, 0, 50, androidx.compose.ui.text.AnnotatedString("A")),
            ReaderLayoutEngine.PageIndexEntry(1, 0, 0, 50, 100, androidx.compose.ui.text.AnnotatedString("B")),
            ReaderLayoutEngine.PageIndexEntry(2, 0, 0, 100, 150, androidx.compose.ui.text.AnnotatedString("C"))
        )
        val pageIndex = ReaderLayoutEngine.ReaderPageIndex(entries)
        assertEquals(0, pageIndex.findPageByOffset(25))
        assertEquals(1, pageIndex.findPageByOffset(50))
        assertEquals(2, pageIndex.findPageByOffset(120))
    }
}
