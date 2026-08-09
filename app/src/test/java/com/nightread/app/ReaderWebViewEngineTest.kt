package com.nightread.app

import com.nightread.app.ui.customlayout.ReaderWebViewEngine
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReaderWebViewEngineTest {

    @Test
    fun testHtmlSanitizationAndWrapping() {
        val rawText = "[CHAPTER]Глава 1[/CHAPTER]\nЭто тестовый абзац с <b>жирным</b> текстом и смайликом 🚀.\n[CITE]Цитата из книги[/CITE]"
        val html = ReaderWebViewEngine.prepareHtmlForBook(
            context = null,
            bookId = "test_book",
            mainText = rawText,
            fontFamily = "Serif",
            fontSize = 18f,
            fontWeight = 500f,
            lineHeight = 1.4f,
            textColorHex = "#000000",
            bgColorHex = "#FFFFFF",
            viewportWidth = 800,
            viewportHeight = 1200
        )

        assertTrue("HTML should contain wrapper", html.contains("<!DOCTYPE html>"))
        assertTrue("HTML should contain chapter", html.contains("Глава 1"))
        assertTrue("HTML should contain bold text", html.contains("<b>жирным</b>"))
        assertTrue("HTML should contain emoji", html.contains("🚀"))
        assertTrue("HTML should contain quote", html.contains("Цитата из книги"))
        assertTrue("HTML should contain data-offset attributes", html.contains("data-offset="))
    }
}
