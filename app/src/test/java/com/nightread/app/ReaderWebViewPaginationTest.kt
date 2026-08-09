package com.nightread.app

import com.nightread.app.ui.customlayout.ReaderWebViewEngine
import com.nightread.app.ui.customlayout.ReaderWebViewPage
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReaderWebViewPaginationTest {

    @Test
    fun testFindPageForOffset() {
        val pages = listOf(
            ReaderWebViewPage(0, 0, 100, "html0"),
            ReaderWebViewPage(1, 101, 250, "html1"),
            ReaderWebViewPage(2, 251, 500, "html2")
        )

        assertEquals("Offset 50 should be on page 0", 0, ReaderWebViewEngine.findPageForOffset(pages, 50))
        assertEquals("Offset 101 should be on page 1", 1, ReaderWebViewEngine.findPageForOffset(pages, 101))
        assertEquals("Offset 300 should be on page 2", 2, ReaderWebViewEngine.findPageForOffset(pages, 300))
        assertEquals("Offset 999 should fallback to 0 or last", 0, ReaderWebViewEngine.findPageForOffset(pages, 999))
    }

    @Test
    fun testFindOffsetForPage() {
        val pages = listOf(
            ReaderWebViewPage(0, 0, 100, "html0"),
            ReaderWebViewPage(1, 101, 250, "html1"),
            ReaderWebViewPage(2, 251, 500, "html2")
        )

        assertEquals("Page 0 start offset", 0, ReaderWebViewEngine.findOffsetForPage(pages, 0))
        assertEquals("Page 1 start offset", 101, ReaderWebViewEngine.findOffsetForPage(pages, 1))
        assertEquals("Page 2 start offset", 251, ReaderWebViewEngine.findOffsetForPage(pages, 2))
        assertEquals("Invalid page index fallback", 0, ReaderWebViewEngine.findOffsetForPage(pages, 5))
    }
}
