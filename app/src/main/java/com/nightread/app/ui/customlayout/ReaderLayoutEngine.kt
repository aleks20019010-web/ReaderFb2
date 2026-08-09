package com.nightread.app.ui.customlayout

import android.content.Context
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak
import kotlinx.coroutines.CoroutineScope
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import android.util.Log
import com.nightread.app.ui.PaginationDiskCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.util.PriorityQueue

object ReaderLayoutEngine {
    private const val TAG = "ReaderLayoutEngine"
    private const val DEBUG_PAGINATION = true
    private const val TARGET_CHUNK_CHAR_COUNT = 4000

    suspend fun parseDocument(bookId: String, mainText: String, baseFontSize: androidx.compose.ui.unit.TextUnit): ReaderDocument = withContext(Dispatchers.Default) {
        val paragraphs = mutableListOf<ReaderParagraph>()
        val chapters = mutableListOf<ReaderChapter>()
        
        var currentGlobalOffset = 0
        var chapterStartOffset = 0
        var currentChapterParas = mutableListOf<ReaderParagraph>()
        var currentChapterTitle = "Начало книги"
        var chapterIndex = 0
        
        val length = mainText.length
        
        fun stripTags(input: String): String {
            val sb = StringBuilder()
            var inTag = false
            var i = 0
            while (i < input.length) {
                val c = input[i]
                if (c == '<') {
                    inTag = true
                    i++
                } else if (c == '>') {
                    if (inTag) inTag = false
                    else sb.append(c)
                    i++
                } else if (input.startsWith("[CHAPTER]", i)) {
                    i += "[CHAPTER]".length
                } else if (input.startsWith("[/CHAPTER]", i)) {
                    i += "[/CHAPTER]".length
                } else {
                    if (!inTag) sb.append(c)
                    i++
                }
            }
            return sb.toString()
        }
        
        while (currentGlobalOffset <= length) {
            val nextNewline = mainText.indexOf('\n', currentGlobalOffset)
            val isLastLine = nextNewline == -1
            val lineEnd = if (isLastLine) length else nextNewline
            
            val lineStart = currentGlobalOffset
            val lineLength = lineEnd - lineStart
            val line = mainText.substring(lineStart, lineEnd)
            
            currentGlobalOffset = lineEnd + 1
            
            var isBlank = true
            for (i in 0 until lineLength) {
                if (!line[i].isWhitespace()) {
                    isBlank = false
                    break
                }
            }
            if (isBlank) continue
            
            val trimmed = line.trim()
            
            var isChapterTitle = false
            if (trimmed.startsWith("<h1") || trimmed.startsWith("<title") || trimmed.startsWith("[CHAPTER]")) {
                isChapterTitle = true
            } else if (trimmed.startsWith("<h1>") && trimmed.endsWith("</h1>")) {
                isChapterTitle = true
            } else if (trimmed.length < 80) {
                if (trimmed.startsWith("Глава") || trimmed.startsWith("Chapter")) {
                    isChapterTitle = true
                } else {
                    val dotIdx = trimmed.indexOf('.')
                    if (dotIdx > 0) {
                        var isRomanOrNum = true
                        for (i in 0 until dotIdx) {
                            val c = trimmed[i]
                            if (!(c in '0'..'9' || c == 'I' || c == 'V' || c == 'X' || c == 'L' || c == 'C' || c == 'D' || c == 'M')) {
                                isRomanOrNum = false
                                break
                            }
                        }
                        if (isRomanOrNum) isChapterTitle = true
                    }
                }
            }
            
            if (isChapterTitle && currentChapterParas.isNotEmpty()) {
                val endOffset = lineStart
                val chunks = buildChunksForChapter(chapterIndex, currentChapterParas, chapterStartOffset, endOffset)
                chapters.add(
                    ReaderChapter(
                        chapterIndex = chapterIndex,
                        title = currentChapterTitle,
                        startOffset = chapterStartOffset,
                        endOffset = endOffset,
                        paragraphs = currentChapterParas,
                        chunks = chunks
                    )
                )
                paragraphs.addAll(currentChapterParas)
                chapterIndex++
                currentChapterParas = mutableListOf()
                chapterStartOffset = lineStart
                currentChapterTitle = stripTags(line).trim().take(60)
                if (currentChapterTitle.isBlank()) currentChapterTitle = "Глава ${chapterIndex + 1}"
            }
            
            val inlines = if (isChapterTitle) {
                val cleanLine = stripTags(line)
                listOf(
                    ReaderInline.Styled(
                        content = cleanLine,
                        style = androidx.compose.ui.text.SpanStyle(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            fontSize = baseFontSize * 1.5f
                        ),
                        globalStartOffset = lineStart,
                        globalEndOffset = lineStart + cleanLine.length
                    )
                )
            } else {
                parseInlines(line, lineStart, lineEnd, baseFontSize)
            }
            
            val actualLine = if (isChapterTitle) stripTags(line) else line
            currentChapterParas.add(
                ReaderParagraph(
                    rawText = actualLine,
                    inlines = inlines,
                    globalStartOffset = lineStart,
                    globalEndOffset = lineStart + actualLine.length
                )
            )
            
            if (isChapterTitle && currentChapterParas.size == 1) {
                currentChapterTitle = actualLine.trim().take(60)
                if (currentChapterTitle.isBlank()) currentChapterTitle = "Глава ${chapterIndex + 1}"
            }
        }
        
        val finalEndOffset = mainText.length
        if (currentChapterParas.isNotEmpty() || chapters.isEmpty()) {
            val chunks = buildChunksForChapter(chapterIndex, currentChapterParas, chapterStartOffset, finalEndOffset)
            chapters.add(
                ReaderChapter(
                    chapterIndex = chapterIndex,
                    title = currentChapterTitle,
                    startOffset = chapterStartOffset,
                    endOffset = finalEndOffset,
                    paragraphs = currentChapterParas,
                    chunks = chunks
                )
            )
            paragraphs.addAll(currentChapterParas)
        }

        ReaderDocument(
            bookId = bookId,
            rawMainText = mainText,
            paragraphs = paragraphs,
            chapters = chapters
        )
    }

    private fun buildChunksForChapter(chapterIndex: Int, paragraphs: List<ReaderParagraph>, chapterStartOffset: Int, chapterEndOffset: Int): List<ReaderChunk> {
        val chunks = mutableListOf<ReaderChunk>()
        if (paragraphs.isEmpty()) {
            chunks.add(ReaderChunk(0, chapterIndex, chapterStartOffset, chapterEndOffset, emptyList()))
            return chunks
        }

        var currentChunkParas = mutableListOf<ReaderParagraph>()
        var currentChars = 0
        var chunkStart = chapterStartOffset

        for (i in paragraphs.indices) {
            val para = paragraphs[i]
            currentChunkParas.add(para)
            currentChars += para.rawText.length
            
            val isLastPara = i == paragraphs.size - 1
            if (currentChars >= TARGET_CHUNK_CHAR_COUNT || isLastPara) {
                val chunkEnd = if (isLastPara) chapterEndOffset else paragraphs[i + 1].globalStartOffset
                chunks.add(
                    ReaderChunk(
                        chunkIndex = chunks.size,
                        chapterIndex = chapterIndex,
                        startOffset = chunkStart,
                        endOffset = chunkEnd,
                        paragraphs = currentChunkParas.toList()
                    )
                )
                currentChunkParas.clear()
                currentChars = 0
                chunkStart = chunkEnd
            }
        }
        return chunks
    }

    private data class TagInfo(val tagName: String, val localStartIndex: Int)

    private fun parseInlines(text: String, lineStart: Int, lineEnd: Int, baseFontSize: androidx.compose.ui.unit.TextUnit): List<ReaderInline> {
        val regex = Regex("<(/?)(b|i|em|s|strike|del|sup|sub|code|title|h1|h2)>", RegexOption.IGNORE_CASE)
        val matches = regex.findAll(text).toList()
        
        if (matches.isEmpty()) {
            return listOf(ReaderInline.Text(text, lineStart, lineEnd))
        }

        val inlines = mutableListOf<ReaderInline>()
        var currentIndex = 0
        val activeStyles = mutableListOf<String>()

        for (match in matches) {
            val matchRange = match.range
            if (matchRange.first > currentIndex) {
                val sub = text.substring(currentIndex, matchRange.first)
                val sOffset = lineStart + currentIndex
                val eOffset = lineStart + matchRange.first
                
                if (activeStyles.isNotEmpty()) {
                    val combinedStyle = getCombinedStyle(activeStyles, baseFontSize)
                    inlines.add(ReaderInline.Styled(sub, combinedStyle, sOffset, eOffset))
                } else {
                    inlines.add(ReaderInline.Text(sub, sOffset, eOffset))
                }
            }

            val fullTag = match.value
            val isClosing = fullTag.startsWith("</")
            val rawTagName = match.groupValues[2].lowercase()
            val tagName = when (rawTagName) {
                "b" -> "strong"
                "i", "em" -> "emphasis"
                "s", "strike", "del" -> "strikethrough"
                "title", "h1", "h2" -> "chapter"
                else -> rawTagName
            }

            if (!isClosing) {
                activeStyles.add(tagName)
            } else {
                activeStyles.remove(tagName)
            }
            currentIndex = matchRange.last + 1
        }

        if (currentIndex < text.length) {
            val sub = text.substring(currentIndex)
            val sOffset = lineStart + currentIndex
            val eOffset = lineStart + text.length
            if (activeStyles.isNotEmpty()) {
                val combinedStyle = getCombinedStyle(activeStyles, baseFontSize)
                inlines.add(ReaderInline.Styled(sub, combinedStyle, sOffset, eOffset))
            } else {
                inlines.add(ReaderInline.Text(sub, sOffset, eOffset))
            }
        }

        return inlines
    }

    private fun getCombinedStyle(tagNames: List<String>, baseFontSize: androidx.compose.ui.unit.TextUnit): androidx.compose.ui.text.SpanStyle {
        var weight: androidx.compose.ui.text.font.FontWeight? = null
        var style: androidx.compose.ui.text.font.FontStyle? = null
        var decoration: androidx.compose.ui.text.style.TextDecoration? = null
        var baseline: androidx.compose.ui.text.style.BaselineShift? = null
        var size = androidx.compose.ui.unit.TextUnit.Unspecified
        var bg = androidx.compose.ui.graphics.Color.Unspecified
        var fontFam: androidx.compose.ui.text.font.FontFamily? = null

        for (tagName in tagNames) {
            when (tagName) {
                "strong" -> weight = androidx.compose.ui.text.font.FontWeight.Bold
                "emphasis" -> style = androidx.compose.ui.text.font.FontStyle.Italic
                "strikethrough" -> decoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                "sup" -> { baseline = androidx.compose.ui.text.style.BaselineShift.Superscript; size = baseFontSize * 0.75f }
                "sub" -> { baseline = androidx.compose.ui.text.style.BaselineShift.Subscript; size = baseFontSize * 0.75f }
                "code" -> { fontFam = androidx.compose.ui.text.font.FontFamily.Monospace; bg = androidx.compose.ui.graphics.Color(0x22888888) }
                "chapter" -> { weight = androidx.compose.ui.text.font.FontWeight.Bold; size = baseFontSize * 1.5f }
            }
        }

        return androidx.compose.ui.text.SpanStyle(
            fontWeight = weight,
            fontStyle = style,
            textDecoration = decoration,
            baselineShift = baseline,
            fontSize = size,
            background = bg,
            fontFamily = fontFam
        )
    }

    data class AnnotatedMappingResult(
        val annotatedString: AnnotatedString,
        val map: SourceDisplayMap
    )

    fun buildAnnotatedStringForChunk(chunk: ReaderChunk, baseFontSize: androidx.compose.ui.unit.TextUnit): AnnotatedMappingResult {
        val displayToSourceList = mutableListOf<Int>()
        val sourceToDisplayStartMap = mutableMapOf<Int, Int>()
        val sourceToDisplayEndMap = mutableMapOf<Int, Int>()

        val annotated = androidx.compose.ui.text.buildAnnotatedString {
            for ((index, paragraph) in chunk.paragraphs.withIndex()) {
                if (index > 0) {
                    append("\n")
                    val newlineOffset = (paragraph.globalStartOffset - 1).coerceAtLeast(chunk.startOffset)
                    val displayIdx = length - 1
                    displayToSourceList.add(newlineOffset)
                    sourceToDisplayStartMap.putIfAbsent(newlineOffset, displayIdx)
                    sourceToDisplayEndMap[newlineOffset] = displayIdx + 1
                }
                for (inline in paragraph.inlines) {
                    when (inline) {
                        is ReaderInline.Text -> {
                            val content = inline.content
                            val startAnnotatedIndex = length
                            append(content)
                            val startOffset = inline.globalStartOffset
                            for (i in content.indices) {
                                val displayIdx = startAnnotatedIndex + i
                                val sourceOffset = startOffset + i
                                displayToSourceList.add(sourceOffset)
                                sourceToDisplayStartMap.putIfAbsent(sourceOffset, displayIdx)
                                sourceToDisplayEndMap[sourceOffset] = displayIdx + 1
                            }
                        }
                        is ReaderInline.Styled -> {
                            val content = inline.content
                            val startAnnotatedIndex = length
                            append(content)
                            addStyle(inline.style, startAnnotatedIndex, length)
                            val startOffset = inline.globalStartOffset
                            for (i in content.indices) {
                                val displayIdx = startAnnotatedIndex + i
                                val sourceOffset = startOffset + i
                                displayToSourceList.add(sourceOffset)
                                sourceToDisplayStartMap.putIfAbsent(sourceOffset, displayIdx)
                                sourceToDisplayEndMap[sourceOffset] = displayIdx + 1
                            }
                        }
                    }
                }
            }
        }

        val displayToSourceArr = displayToSourceList.toIntArray()
        val maxSource = (displayToSourceArr.maxOrNull() ?: chunk.endOffset) + 1
        val s2dStart = IntArray(maxSource) { -1 }
        val s2dEnd = IntArray(maxSource) { -1 }
        for ((k, v) in sourceToDisplayStartMap) {
            if (k in s2dStart.indices) s2dStart[k] = v
        }
        for ((k, v) in sourceToDisplayEndMap) {
            if (k in s2dEnd.indices) s2dEnd[k] = v
        }

        val map = SourceDisplayMap(
            displayToSource = displayToSourceArr,
            sourceToDisplayStart = s2dStart,
            sourceToDisplayEnd = s2dEnd
        )
        return AnnotatedMappingResult(annotated, map)
    }

    fun createPager(
        context: Context,
        document: ReaderDocument,
        config: ReaderConfiguration,
        viewport: ReaderViewport,
        textMeasurer: TextMeasurer,
        scope: CoroutineScope,
        initialTargetOffset: Int = 0
    ): ReaderPager {
        ReaderMetrics.startSession()
        val layoutKey = buildLayoutKey(document.bookId, config)
        return ReaderPager(document, config, viewport, textMeasurer, context, layoutKey, scope, initialTargetOffset)
    }

    private fun buildLayoutKey(bookId: String, config: ReaderConfiguration): String {
        return "${bookId}_${config.fontFamily.hashCode()}_${config.fontSize.value}_${config.fontWeight.weight}_${config.lineSpacing}_${config.maxWidthPx}_${config.maxHeightPx}"
    }

    suspend fun paginateChunkPublic(
        context: Context,
        bookId: String,
        layoutKey: String,
        chapterIndex: Int,
        chunk: ReaderChunk,
        textMeasurer: TextMeasurer,
        textStyle: TextStyle,
        maxWidthPx: Int,
        safeMaxHeightPx: Int
    ): List<ReaderPage> {
        val mappingResult = buildAnnotatedStringForChunk(chunk, textStyle.fontSize)
        val annotated = mappingResult.annotatedString
        val map = mappingResult.map

        if (annotated.isEmpty()) return emptyList()

        val layoutResult = textMeasurer.measure(
            text = annotated,
            style = textStyle,
            constraints = Constraints(maxWidth = maxWidthPx)
        )

        val lines = mutableListOf<LayoutLine>()
        for (i in 0 until layoutResult.lineCount) {
            val lineStartChar = layoutResult.getLineStart(i)
            val lineEndChar = layoutResult.getLineEnd(i, visibleEnd = true)
            val top = layoutResult.getLineTop(i)
            val bottom = layoutResult.getLineBottom(i)
            val height = bottom - top

            val startSource = if (lineStartChar in map.displayToSource.indices) map.displayToSource[lineStartChar] else chunk.startOffset
            val endSource = if (lineEndChar > 0 && (lineEndChar - 1) in map.displayToSource.indices) {
                map.displayToSource[lineEndChar - 1] + 1
            } else {
                chunk.endOffset
            }

            lines.add(
                LayoutLine(
                    startOffset = startSource.coerceAtMost(chunk.endOffset),
                    endOffset = endSource.coerceAtMost(chunk.endOffset),
                    top = top,
                    bottom = bottom,
                    height = height
                )
            )
        }

        if (lines.isEmpty()) return emptyList()

        val chunkPages = mutableListOf<ReaderPage>()
        var pageStartLineIdx = 0
        var localPageIndex = 0

        while (pageStartLineIdx < lines.size) {
            val pageTop = lines[pageStartLineIdx].top
            var pageEndLineIdx = pageStartLineIdx

            while (pageEndLineIdx < lines.size) {
                val candidateLine = lines[pageEndLineIdx]
                val pageHeight = candidateLine.bottom - pageTop
                if (pageHeight <= safeMaxHeightPx || pageEndLineIdx == pageStartLineIdx) {
                    if (candidateLine.height > safeMaxHeightPx && pageEndLineIdx == pageStartLineIdx) {
                        Log.w(TAG, "OVERSIZED_LINE: line height ${candidateLine.height} exceeds maxHeightPx $safeMaxHeightPx")
                        pageEndLineIdx++
                        break
                    }
                    pageEndLineIdx++
                } else {
                    break
                }
            }

            val pageLines = lines.subList(pageStartLineIdx, pageEndLineIdx)
            val startSource = if (pageStartLineIdx == 0) chunk.startOffset else pageLines.first().startOffset
            val endSource = if (pageEndLineIdx == lines.size) chunk.endOffset else lines[pageEndLineIdx].startOffset

            val startAnnotated = layoutResult.getLineStart(pageStartLineIdx)
            val endAnnotated = layoutResult.getLineEnd(pageEndLineIdx - 1, visibleEnd = true)

            val slice = if (startAnnotated < endAnnotated && endAnnotated <= annotated.length) {
                annotated.subSequence(startAnnotated, endAnnotated).trimTrailingWhitespace()
            } else {
                androidx.compose.ui.text.AnnotatedString("")
            }

            if (slice.isNotEmpty() || startSource < endSource) {
                chunkPages.add(
                    ReaderPage(
                        pageIndex = localPageIndex++,
                        text = slice,
                        startOffset = startSource,
                        endOffset = endSource,
                        startDisplayOffset = startAnnotated,
                        endDisplayOffset = endAnnotated
                    )
                )
            }

            pageStartLineIdx = pageEndLineIdx
        }

        return chunkPages
    }

    fun AnnotatedString.trimTrailingWhitespace(): AnnotatedString {
        var end = length
        while (end > 0 && text[end - 1].isWhitespace()) {
            end--
        }
        return if (end == length) this else subSequence(0, end)
    }

    class ReaderPageIndexBuilder {
        private val entries = mutableListOf<PageIndexEntry>()

        fun addPagesForChunk(chapterIndex: Int, chunkIndex: Int, pages: List<ReaderPage>) {
            // Remove existing for same chapter/chunk if re-paginating
            entries.removeAll { it.chapterIndex == chapterIndex && it.chunkIndex == chunkIndex }
            for (p in pages) {
                entries.add(
                    PageIndexEntry(
                        pageIndex = 0, // will reassign globally
                        chapterIndex = chapterIndex,
                        chunkIndex = chunkIndex,
                        startOffset = p.startOffset,
                        endOffset = p.endOffset,
                        text = p.text
                    )
                )
            }
            entries.sortBy { it.startOffset }
        }

        fun buildSnapshot(): ReaderPageIndex {
            val normalized = entries.mapIndexed { idx, entry ->
                entry.copy(pageIndex = idx)
            }
            return ReaderPageIndex(normalized)
        }
    }

    data class PageIndexEntry(
        val pageIndex: Int,
        val chapterIndex: Int,
        val chunkIndex: Int,
        val startOffset: Int,
        val endOffset: Int,
        val text: AnnotatedString
    ) {
        fun toReaderPage(): ReaderPage = ReaderPage(
            pageIndex = pageIndex,
            text = text,
            startOffset = startOffset,
            endOffset = endOffset
        )
    }

    class ReaderPageIndex(private val entries: List<PageIndexEntry>) {
        fun getPage(index: Int): PageIndexEntry? = entries.getOrNull(index)

        fun findPageByOffset(offset: Int): Int {
            if (entries.isEmpty()) return 0
            var low = 0
            var high = entries.size - 1
            var best = 0
            while (low <= high) {
                val mid = (low + high) ushr 1
                val entry = entries[mid]
                if (offset >= entry.startOffset) {
                    best = mid
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }
            return best
        }

        fun pageCount(): Int = entries.size

        fun allEntries(): List<PageIndexEntry> = entries
    }

    fun validatePagination(pages: List<ReaderPage>, maxHeightPx: Int): PaginationValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        for (i in pages.indices) {
            val page = pages[i]
            if (page.pageIndex != i) {
                errors.add("Page index mismatch at position $i: expected $i, got ${page.pageIndex}")
            }
            if (page.startOffset < 0 || page.endOffset < page.startOffset) {
                errors.add("Invalid page range on page ${page.pageIndex}: start=${page.startOffset}, end=${page.endOffset}")
            }
            if (i > 0) {
                val prev = pages[i - 1]
                if (prev.endOffset > page.startOffset) {
                    errors.add("OVERLAP between page ${prev.pageIndex} (ends ${prev.endOffset}) and page ${page.pageIndex} (starts ${page.startOffset})")
                } else if (prev.endOffset < page.startOffset) {
                    warnings.add("GAP between page ${prev.pageIndex} (ends ${prev.endOffset}) and page ${page.pageIndex} (starts ${page.startOffset})")
                }
            }
        }

        val isValid = errors.isEmpty()
        if (DEBUG_PAGINATION) {
            Log.d(TAG, "Pagination validation: isValid=$isValid, errors=${errors.size}, warnings=${warnings.size}")
        }
        return PaginationValidationResult(isValid, errors, warnings)
    }
}
