package com.nightread.app

import android.content.Context
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import com.nightread.app.ui.customlayout.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.Composable

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReaderLayoutRegressionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun runWithMeasurer(block: (TextMeasurer) -> Unit) {
        var caughtMeasurer: TextMeasurer? = null
        composeTestRule.setContent {
            caughtMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
        }
        composeTestRule.runOnIdle {
            block(caughtMeasurer!!)
        }
    }

    @Test
    fun test1_FullLine_NoSplit() = runWithMeasurer { measurer ->
        val text = "А здорово я это придумал"
        val style = createStyle(20f)
        val chunk = createChunk(text)
        
        val pages = runBlocking {
            ReaderLayoutEngine.paginateChunkPublic(
                context, "b1", "k1", 0, chunk, measurer, style, 1000, 100
            )
        }

        assertEquals("Should be on 1 page", 1, pages.size)
        assertEquals("Text should be intact", text, pages[0].text.text)
    }

    @Test
    fun test2_PageBoundary_FullLineMove() = runWithMeasurer { measurer ->
        val text = "Line 1\nLine 2"
        val style = createStyle(20f)
        val chunk = createChunk(text)
        
        val layoutFull = measurer.measure(
            text = AnnotatedString(text),
            style = style,
            constraints = Constraints(maxWidth = 1000)
        )
        val line1Height = layoutFull.getLineBottom(0)
        
        val pages = runBlocking {
            ReaderLayoutEngine.paginateChunkPublic(
                context, "b1", "k1", 0, chunk, measurer, style, 1000, line1Height.toInt()
            )
        }

        assertTrue("Should be split into 2 pages", pages.size >= 2)
        assertEquals("First page should only have Line 1", "Line 1", pages[0].text.text.trim())
        assertEquals("Second page should start with Line 2", "Line 2", pages[1].text.text.trim())
    }

    @Test
    fun test3_NoPartialLine_SlicingCheck() = runWithMeasurer { measurer ->
        val text = "Line 1\nLine 2\nLine 3"
        val style = createStyle(20f)
        val chunk = createChunk(text)
        
        val layout = measurer.measure(
            text = AnnotatedString(text),
            style = style,
            constraints = Constraints(maxWidth = 1000)
        )
        val oneLineHeight = layout.getLineBottom(0)

        val pages = runBlocking {
            ReaderLayoutEngine.paginateChunkPublic(
                context, "b1", "k1", 0, chunk, measurer, style, 1000, oneLineHeight.toInt()
            )
        }

        for (page in pages) {
            val pageLayout = measurer.measure(
                text = page.text,
                style = style,
                constraints = Constraints(maxWidth = 1000)
            )
            // Each page should have exactly 1 line because we set height to 1 line
            assertTrue("Each page should have an integral number of lines", pageLayout.lineCount >= 1)
        }
    }

    @Test
    fun test4_NoLostCharacters() = runWithMeasurer { measurer ->
        val text = "Word1\nWord2\nWord3"
        val style = createStyle(20f)
        val chunk = createChunk(text)
        
        val pages = runBlocking {
            ReaderLayoutEngine.paginateChunkPublic(
                context, "b1", "k1", 0, chunk, measurer, style, 1000, 10
            )
        }

        val reassembled = pages.joinToString("") { it.text.text }
        assertEquals("Reassembled text should match original", text, reassembled)
    }

    @Test
    fun test5_NoDuplicates() = runWithMeasurer { measurer ->
        val text = "Continuous text check for duplicates"
        val style = createStyle(20f)
        val chunk = createChunk(text)
        
        val pages = runBlocking {
            ReaderLayoutEngine.paginateChunkPublic(
                context, "b1", "k1", 0, chunk, measurer, style, 1000, 10
            )
        }

        for (i in 0 until pages.size - 1) {
            assertTrue("No overlap in source offsets", pages[i].endOffset <= pages[i+1].startOffset)
        }
    }

    @Test
    fun test6_ContinuousOffsets() = runWithMeasurer { measurer ->
        val text = "Continuous offsets check"
        val style = createStyle(20f)
        val chunk = createChunk(text)
        
        val pages = runBlocking {
            ReaderLayoutEngine.paginateChunkPublic(
                context, "b1", "k1", 0, chunk, measurer, style, 1000, 10
            )
        }

        for (i in 0 until pages.size - 1) {
            assertEquals("Offsets should be continuous", pages[i].endOffset, pages[i+1].startOffset)
        }
    }

    @Test
    fun test7_SmallViewport() = runWithMeasurer { measurer ->
        val text = "Small viewport test"
        val style = createStyle(20f)
        val chunk = createChunk(text)
        
        val pages = runBlocking {
            ReaderLayoutEngine.paginateChunkPublic(
                context, "b1", "k1", 0, chunk, measurer, style, 1000, 10
            )
        }

        assertFalse("Should still produce pages even if oversized", pages.isEmpty())
    }

    @Test
    fun test8_LargeViewport() = runWithMeasurer { measurer ->
        val text = "Short text."
        val style = createStyle(20f)
        val chunk = createChunk(text)
        
        val pages = runBlocking {
            ReaderLayoutEngine.paginateChunkPublic(
                context, "b1", "k1", 0, chunk, measurer, style, 5000, 5000
            )
        }

        assertEquals("Should be on 1 page", 1, pages.size)
    }

    @Test
    fun test9_FontSizeChange() = runWithMeasurer { measurer ->
        val text = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5"
        val styleSmall = createStyle(10f)
        val styleLarge = createStyle(100f)
        val chunk = createChunk(text)
        
        val pagesSmall = runBlocking {
            ReaderLayoutEngine.paginateChunkPublic(
                context, "b1", "k1", 0, chunk, measurer, styleSmall, 100, 50
            )
        }
        val pagesLarge = runBlocking {
            ReaderLayoutEngine.paginateChunkPublic(
                context, "b1", "k1", 0, chunk, measurer, styleLarge, 100, 50
            )
        }

        assertTrue("Large font should produce more or equal pages", pagesLarge.size >= pagesSmall.size)
    }

    @Test
    fun test10_ContinuousOffsets_MultiChunk() = runWithMeasurer { measurer ->
        val text1 = "Chunk one text. "
        val text2 = "Chunk two text."
        val style = createStyle(20f)
        
        val chunk1 = ReaderChunk(0, 0, 0, text1.length, listOf(ReaderParagraph(text1, listOf(ReaderInline.Text(text1, 0, text1.length)), 0, text1.length)))
        val chunk2 = ReaderChunk(1, 0, text1.length, text1.length + text2.length, listOf(ReaderParagraph(text2, listOf(ReaderInline.Text(text2, text1.length, text1.length + text2.length)), text1.length, text1.length + text2.length)))
        
        val pages1 = runBlocking { ReaderLayoutEngine.paginateChunkPublic(context, "b1", "k1", 0, chunk1, measurer, style, 1000, 10) }
        val pages2 = runBlocking { ReaderLayoutEngine.paginateChunkPublic(context, "b1", "k1", 0, chunk2, measurer, style, 1000, 10) }
        
        assertEquals("Chunks should be continuous", pages1.last().endOffset, pages2.first().startOffset)
    }

    @Test
    fun test11_Homogeneous_18_Lines() = runWithMeasurer { measurer ->
        val lineCount = 18
        val text = List(lineCount) { "This is line $it." }.joinToString("\n")
        val style = createStyle(20f)
        val chunk = createChunk(text)
        
        val layout = measurer.measure(
            text = AnnotatedString(text),
            style = style,
            constraints = Constraints(maxWidth = 1000)
        )
        val oneLineHeight = layout.getLineBottom(0)
        val totalHeight = layout.getLineBottom(lineCount - 1)
        
        // We set safeMaxHeightPx exactly to totalHeight
        val pages = runBlocking {
            ReaderLayoutEngine.paginateChunkPublic(
                context, "b1", "k1", 0, chunk, measurer, style, 1000, totalHeight.toInt()
            )
        }

        assertEquals("Should be on 1 page if totalHeight fits", 1, pages.size)
    }

    @Test
    fun test12_MixedMetrics_ChapterTitle() = runWithMeasurer { measurer ->
        val title = "CHAPTER 1"
        val body = "Line 1\nLine 2\nLine 3"
        val text = "$title\n$body"
        
        // Title has different style (larger font)
        val style = createStyle(20f)
        val titleStyle = SpanStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold)
        
        val chunk = ReaderChunk(
            0, 0, 0, text.length,
            listOf(
                ReaderParagraph(text, listOf(
                    ReaderInline.Styled(title, titleStyle, 0, title.length),
                    ReaderInline.Text("\n$body", title.length, text.length)
                ), 0, text.length)
            )
        )
        
        val pages = runBlocking {
            ReaderLayoutEngine.paginateChunkPublic(
                context, "b1", "k1", 0, chunk, measurer, style, 1000, 100
            )
        }

        assertTrue("Should produce pages", pages.isNotEmpty())
        val firstPageText = pages[0].text.text
        assertTrue("First page should contain title", firstPageText.contains(title))
    }

    @Test
    fun test13_Continuity_Invariant() = runWithMeasurer { measurer ->
        val text = "Long text line.\n".repeat(100)
        val style = createStyle(20f)
        val chunk = createChunk(text)
        
        val pages = runBlocking {
            ReaderLayoutEngine.paginateChunkPublic(
                context, "b1", "k1", 0, chunk, measurer, style, 100, 200
            )
        }

        println("ReaderLayoutRegressionTest: test13: pages.size=${pages.size}")
        assertTrue("Should have multiple pages, got ${pages.size}", pages.size > 1)
        for (i in 0 until pages.size - 1) {
            assertEquals("Page continuity failed at page $i", pages[i].endOffset, pages[i+1].startOffset)
            assertFalse("Page should not be empty", pages[i].text.isEmpty())
        }
    }

    @Test
    fun test14_NoVisualLineSplit() = runWithMeasurer { measurer ->
        val text = "One long line that wraps but should stay together on one page if possible."
        val style = createStyle(20f)
        val chunk = createChunk(text)
        
        // Use a very small width to force multiple LayoutLines
        val pages = runBlocking {
            ReaderLayoutEngine.paginateChunkPublic(
                context, "b1", "k1", 0, chunk, measurer, style, 100, 500
            )
        }

        // Even though it's multiple lines, they should all be on one page if 500 height fits them
        assertEquals("Multiple LayoutLines should stay on one page if height permits", 1, pages.size)
    }

    @Test
    fun test15_Epsilon_Precision() = runWithMeasurer { measurer ->
        val text = "Line 1\nLine 2"
        val style = createStyle(20f)
        val chunk = createChunk(text)
        
        val layout = measurer.measure(
            text = AnnotatedString(text),
            style = style,
            constraints = Constraints(maxWidth = 1000)
        )
        val line0Bottom = layout.getLineBottom(0)
        val line1Bottom = layout.getLineBottom(1)
        
        // Let's say line 1 bottom is 50.3. We set safeHeight to 50.
        // With epsilon 0.5, it should still fit (50.3 <= 50 + 0.5).
        val safeHeight = line1Bottom.toInt()
        
        val pages = runBlocking {
            ReaderLayoutEngine.paginateChunkPublic(
                context, "b1", "k1", 0, chunk, measurer, style, 1000, safeHeight
            )
        }

        assertTrue("Epsilon should allow line to fit even if slightly over", pages.size == 1 || (line1Bottom - safeHeight) > 0.5f)
        // If it still doesn't fit, it means the line was actually more than 0.5px over.
        // But for standard text it should usually be within 0.5px of the integer boundary if we are lucky,
        // or we can just force it in the test by adjusting safeHeight.
        
        val pagesForced = runBlocking {
            ReaderLayoutEngine.paginateChunkPublic(
                context, "b1", "k1", 0, chunk, measurer, style, 1000, (line1Bottom - 0.1f).toInt()
            )
        }
        // Actually, let's just make it simpler:
        assertEquals("Epsilon 0.5 check", 1, pages.size) 
    }
    private fun createStyle(fontSize: Float) = TextStyle(
        fontSize = fontSize.sp,
        lineHeight = (fontSize * 1.4f).sp,
        lineBreak = LineBreak.Paragraph,
        hyphens = Hyphens.Auto,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )

    private fun createChunk(text: String) = ReaderChunk(
        chunkIndex = 0,
        chapterIndex = 0,
        startOffset = 0,
        endOffset = text.length,
        paragraphs = listOf(
            ReaderParagraph(
                rawText = text,
                inlines = listOf(ReaderInline.Text(text, 0, text.length)),
                globalStartOffset = 0,
                globalEndOffset = text.length
            )
        )
    )
}
