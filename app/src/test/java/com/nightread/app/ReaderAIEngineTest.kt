package com.nightread.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nightread.app.ui.customlayout.ReaderEngineType
import com.nightread.app.ui.customlayout.ai.ReaderAIEngine
import com.nightread.app.ui.customlayout.ai.ReaderAIPageValidator
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReaderAIEngineTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ReaderAIEngine.initialize(context, 1080, 1920, 18f, "Serif")
    }

    @Test
    fun testInitializationAndPerformance() {
        val isReady = ReaderAIEngine.isReady()
        assertTrue("Init time should be measured", ReaderAIEngine.getInitTimeMs() >= 0)

        val diag = ReaderAIEngine.getDiagnosticsInfo()
        assertEquals("Qwen2.5-0.5B-Instruct", diag["model_name"])
        assertEquals("llama.cpp + Android NDK", diag["backend"])
        assertNotNull(diag["sha256"])
        assertTrue("Summary should contain model info", ReaderAIEngine.getDiagnosticsSummary().contains("Qwen2.5"))
    }

    @Test
    fun testPaginationInvariants_100Pages() {
        val sampleText = StringBuilder().apply {
            for (i in 1..200) {
                append("Абзац $i: Это тестовый текст для проверки работы AI Reader Engine. Раздел $i содержит эмодзи 🌟, диалоги — Привет! — И тебе привет!, а также заголовки [CHAPTER]Глава $i[/CHAPTER]. ")
            }
        }.toString()

        val pages = ReaderAIEngine.paginateBook(
            context = context,
            bookId = "test_100",
            mainText = sampleText,
            fontFamily = "Serif",
            fontSize = 18f,
            fontWeight = 400f,
            lineHeight = 1.3f,
            textColorHex = "#000000",
            bgColorHex = "#FFFFFF",
            viewportWidth = 1080,
            viewportHeight = 1920
        )

        assertTrue("Pages should be generated", pages.isNotEmpty())

        var expectedOffset = 0
        for (i in pages.indices) {
            val page = pages[i]
            assertEquals("Page index mismatch", i, page.pageIndex)
            assertEquals("Page start offset mismatch on page $i", expectedOffset, page.pageStartOffset)
            assertTrue("Page end offset must be strictly greater than start", page.pageEndOffset > page.pageStartOffset)
            expectedOffset = page.pageEndOffset
        }
        assertEquals("Total length covered should match text length", sampleText.length, expectedOffset)
    }

    @Test
    fun testValidatorAndFallback() {
        val validator = ReaderAIPageValidator()
        val text = "Тестовая страница"
        val layout = com.nightread.app.ui.customlayout.ai.ReaderAIPageLayout(
            pageIndex = 0,
            pageStartOffset = 0,
            pageEndOffset = text.length,
            pageText = text,
            htmlContent = "<p>$text</p>",
            heightUsedPx = 500f,
            safeHeightPx = 1000f
        )

        val result = validator.validatePage(layout, 1000f, prevEndOffset = null)
        assertTrue("Valid layout should pass validation", result.isValid)

        val invalidLayout = layout.copy(heightUsedPx = 1500f)
        val resultInvalid = validator.validatePage(invalidLayout, 1000f, prevEndOffset = null)
        assertFalse("Overflow layout should fail validation", resultInvalid.isValid)
    }

    @Test
    fun testEngineSwitchingEnum() {
        val types = ReaderEngineType.values()
        assertTrue("Enum must contain CUSTOM", types.contains(ReaderEngineType.CUSTOM))
        assertTrue("Enum must contain WEBVIEW", types.contains(ReaderEngineType.WEBVIEW))
        assertTrue("Enum must contain AI", types.contains(ReaderEngineType.AI))
    }
}
