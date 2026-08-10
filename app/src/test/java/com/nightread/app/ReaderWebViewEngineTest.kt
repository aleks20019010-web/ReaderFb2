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

    @Test
    fun testNoContentBelowViewportCssAndDiagnostics() {
        val rawText = """
            [CHAPTER]Глава 2. Тестирование макета[/CHAPTER]
            Длинный абзац с обычным текстом, <b>жирным выделением</b>, <i>курсивом</i>, <u>подчеркиванием</u> и эмодзи 🌟🚀.
            — Привет.
            — Привет, как дела?
            — Всё хорошо, проверяем макет книги на физических устройствах.
            <img src="test.png" alt="test" />
        """.trimIndent()

        val viewportWidth = 600
        val viewportHeight = 1000

        val html = ReaderWebViewEngine.prepareHtmlForBook(
            context = null,
            bookId = "viewport_test",
            mainText = rawText,
            fontFamily = "Serif",
            fontSize = 16f,
            fontWeight = 400f,
            lineHeight = 1.4f,
            textColorHex = "#111111",
            bgColorHex = "#FFFFFF",
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight
        )

        // Verify viewport bounds and safety padding
        assertTrue("HTML must set overflow: hidden for viewport boundary containment", html.contains("overflow: hidden;"))
        assertTrue("HTML must set side padding to 8dp", html.contains("padding: 12px 8px 12px 8px;"))
        
        // Verify image max height calculation to avoid vertical overflow
        assertTrue("Image max-height must be constrained to viewportHeight - safety margin", html.contains("max-height: calc(100vh - 80px);"))

        // Verify normalized paragraph margin to eliminate excess space
        assertTrue("Paragraph bottom margin must be normalized (0)", html.contains("margin-bottom: 0;"))

        // Verify diagnostic script checks scrollHeight <= clientHeight
        assertTrue("Diagnostic script must check vertical overflow (sh > ch + 2)", html.contains("verticalOverflow = sh > ch + 2"))
        assertTrue("Diagnostic script must log WEBVIEW_DIAGNOSTIC", html.contains("[WEBVIEW_DIAGNOSTIC]"))
    }

    @Test
    fun testDialogueFormattingNoExcessiveMargins() {
        val dialogueText = """
            — Привет.
            — Привет, как дела?
            — Всё отлично!
        """.trimIndent()

        val html = ReaderWebViewEngine.prepareHtmlForBook(
            context = null,
            bookId = "dialogue_test",
            mainText = dialogueText,
            fontFamily = "Sans-Serif",
            fontSize = 16f,
            fontWeight = 400f,
            lineHeight = 1.3f,
            textColorHex = "#000000",
            bgColorHex = "#FFFFFF",
            viewportWidth = 400,
            viewportHeight = 800
        )

        assertTrue("Dialogue lines should be wrapped in paragraph tags", html.contains("<p data-offset=\"0\">— Привет.</p>"))
        assertTrue("Paragraph margin resets default browser margin block", html.contains("margin-block-start: 0;"))
        assertTrue("Paragraph margin resets default browser margin block end", html.contains("margin-block-end: 0;"))
    }
}

