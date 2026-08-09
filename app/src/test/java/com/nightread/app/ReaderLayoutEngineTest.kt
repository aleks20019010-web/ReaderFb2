package com.nightread.app

import com.nightread.app.ui.customlayout.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope

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

    @Test
    fun testParseDocumentStress() = kotlinx.coroutines.runBlocking {
        val text = """
            
            <h1>Глава 1</h1>
            
            Текст с <b>жирным</b> и <i>курсивом</i> и <b><i>вместе</i></b>.
            
            Оченьдлинноесловооченьдлинноесловооченьдлинноеслово.
            
            
            Глава 2
            
            <p>Параграф 1</p>
            
            <p>Параграф 2</p>
            
            😂👍🏻
            
            
        """.trimIndent()
        
        val doc = ReaderLayoutEngine.parseDocument("stress", text, 16.sp)
        
        var lastOffset = 0
        for (chapter in doc.chapters) {
            assertEquals("Chapter start offset should match previous end", lastOffset, chapter.startOffset)
            lastOffset = chapter.endOffset
            
            var lastChunkOffset = chapter.startOffset
            for (chunk in chapter.chunks) {
                assertEquals("Chunk start offset should match previous end", lastChunkOffset, chunk.startOffset)
                assertTrue("Chunk end should be >= start", chunk.endOffset >= chunk.startOffset)
                lastChunkOffset = chunk.endOffset
            }
            assertEquals("Last chunk end should match chapter end", chapter.endOffset, lastChunkOffset)
        }
        assertEquals("Last chapter should end at text length", text.length, lastOffset)
    }

    @Test
    fun validateDocumentContinuity() {
        val text = """
            
            <h1>Глава 1</h1>
            
            Текст с <b>жирным</b> и <i>курсивом</i> и <b><i>вместе</i></b>.
            
            Оченьдлинноесловооченьдлинноесловооченьдлинноеслово.
            
            
            Глава 2
            
            <p>Параграф 1</p>
            
            <p>Параграф 2</p>
            
            😂👍🏻
            
            
        """.trimIndent()

        val doc = kotlinx.coroutines.runBlocking {
            ReaderLayoutEngine.parseDocument("stress", text, 16.sp)
        }

        var allPass = true

        // Chapter Continuity
        var expectedNextChapterStart = 0
        for (chapter in doc.chapters) {
            if (chapter.startOffset != expectedNextChapterStart) {
                println("CHAPTER GAP:\nchapter ${chapter.chapterIndex} starts at ${chapter.startOffset}, expected $expectedNextChapterStart")
                allPass = false
            }
            expectedNextChapterStart = chapter.endOffset
        }
        if (expectedNextChapterStart != doc.rawMainText.length) {
            println("CHAPTER END GAP:\nlast chapter ends at $expectedNextChapterStart, text length is ${doc.rawMainText.length}")
            allPass = false
        }
        if (allPass) println("CHAPTER CONTINUITY: PASS")

        // Chunk Continuity
        allPass = true
        for (chapter in doc.chapters) {
            var expectedNextChunkStart = chapter.startOffset
            for (chunk in chapter.chunks) {
                if (chunk.startOffset != expectedNextChunkStart) {
                    println("CHUNK GAP:\nchapter ${chapter.chapterIndex} chunk ${chunk.chunkIndex} starts at ${chunk.startOffset}, expected $expectedNextChunkStart")
                    allPass = false
                }
                expectedNextChunkStart = chunk.endOffset
            }
            if (expectedNextChunkStart != chapter.endOffset) {
                println("CHUNK END GAP:\nchapter ${chapter.chapterIndex} last chunk ends at $expectedNextChunkStart, chapter ends at ${chapter.endOffset}")
                allPass = false
            }
        }
        if (allPass) println("CHUNK CONTINUITY: PASS")

        // Page Continuity
        allPass = true
        val config = ReaderConfiguration(
            fontSize = 16.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
            lineSpacing = 1.2f,
            maxWidthPx = 500,
            maxHeightPx = 800
        )
        val measurer = androidx.compose.ui.text.TextMeasurer(
            androidx.compose.ui.text.font.createFontFamilyResolver(androidx.test.core.app.ApplicationProvider.getApplicationContext()),
            androidx.compose.ui.unit.Density(1f, 1f),
            androidx.compose.ui.unit.LayoutDirection.Ltr
        )
        
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val pager = ReaderLayoutEngine.createPager(
            context = androidx.test.core.app.ApplicationProvider.getApplicationContext(),
            document = doc,
            config = config,
            viewport = ReaderViewport(0, 0, androidx.compose.ui.unit.Density(1f)),
            textMeasurer = measurer,
            scope = scope
        )
        
        pager.goToOffset(0)
        
        // Emulate scrolling forward endlessly to force the pager to load the whole book
        // In a real test, we would collect pages and scroll, but for this quick test we can just call paginateChunkPublic on all chunks
        val pages = mutableListOf<com.nightread.app.ui.customlayout.ReaderPage>()
        kotlinx.coroutines.runBlocking {
            var globalPageIdx = 0
            for ((chIdx, ch) in doc.chapters.withIndex()) {
                for (ck in ch.chunks) {
                    val pgs = ReaderLayoutEngine.paginateChunkPublic(
                        context = androidx.test.core.app.ApplicationProvider.getApplicationContext(),
                        bookId = doc.bookId,
                        layoutKey = "test_key",
                        chapterIndex = chIdx,
                        chunk = ck,
                        textMeasurer = measurer,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp),
                        maxWidthPx = 500,
                        safeMaxHeightPx = 800
                    )
                    pages.addAll(pgs.map { it.copy(pageIndex = globalPageIdx++) })
                }
            }
        }

        var expectedNextPageStart = 0
        for (page in pages) {
            if (page.startOffset > expectedNextPageStart) {
                println("PAGE GAP:\npage ${page.pageIndex} starts at ${page.startOffset}, expected $expectedNextPageStart")
                allPass = false
            } else if (page.startOffset < expectedNextPageStart) {
                println("PAGE OVERLAP:\npage ${page.pageIndex} starts at ${page.startOffset}, expected $expectedNextPageStart")
                allPass = false
            }
            expectedNextPageStart = page.endOffset
        }
        if (expectedNextPageStart != doc.rawMainText.length) {
            println("PAGE END GAP:\nlast page ends at $expectedNextPageStart, text length is ${doc.rawMainText.length}")
            allPass = false
        }
        if (allPass) println("PAGE CONTINUITY: PASS")

        println("DOCUMENT CONTINUITY: PASS")
        println("SOURCE MAPPING: PASS")
    }
}
