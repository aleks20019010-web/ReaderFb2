package com.nightread.app

import com.nightread.app.ui.customlayout.ReaderWebViewEngine
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReaderWebViewSourceMappingTest {

    @Test
    fun testSourceOffsetMappingPreservation() {
        val rawText = "Первый абзац книги.\nВторой абзац книги с кириллицей и латиницей Latin text.\nТретий абзац с emoji 🌟."
        val html = ReaderWebViewEngine.prepareHtmlForBook(
            context = null,
            bookId = "mapping_test",
            mainText = rawText,
            fontFamily = "Sans-Serif",
            fontSize = 16f,
            fontWeight = 400f,
            lineHeight = 1.3f,
            textColorHex = "#333333",
            bgColorHex = "#F5F5F5",
            viewportWidth = 600,
            viewportHeight = 900
        )

        assertTrue("HTML should preserve source text", html.contains("Первый абзац книги."))
        assertTrue("HTML should preserve latin text", html.contains("Latin text"))
        assertTrue("HTML should preserve emoji", html.contains("🌟"))
        assertTrue("HTML should contain data-offset annotations for mapping", html.contains("data-offset=\"0\""))
    }
}
