package com.nightread.app.ui.customlayout

import android.content.Context
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak
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
        val lines = mainText.split('\n')
        var currentGlobalOffset = 0

        val chapterIndices = mutableListOf<Int>()
        chapterIndices.add(0)

        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("<h1") || trimmed.startsWith("<title") || trimmed.startsWith("[CHAPTER]") ||
                (trimmed.startsWith("<h1>") && trimmed.endsWith("</h1>")) ||
                (trimmed.length < 80 && (trimmed.startsWith("Глава") || trimmed.startsWith("Chapter") || trimmed.matches(Regex("^[0-9IVXLCDM]+\\..*"))))) {
                if (index > 0 && !chapterIndices.contains(index)) {
                    chapterIndices.add(index)
                }
            }
        }
        chapterIndices.add(lines.size)

        for (paraIdx in lines.indices) {
            val line = lines[paraIdx]
            val lineStart = currentGlobalOffset
            val lineEnd = lineStart + line.length
            val inlines = parseInlines(line, lineStart, lineEnd, baseFontSize)
            paragraphs.add(
                ReaderParagraph(
                    rawText = line,
                    inlines = inlines,
                    globalStartOffset = lineStart,
                    globalEndOffset = lineEnd
                )
            )
            currentGlobalOffset = lineEnd + 1
        }

        for (i in 0 until chapterIndices.size - 1) {
            val startLine = chapterIndices[i]
            val endLine = chapterIndices[i + 1].coerceAtMost(paragraphs.size)
            if (startLine >= endLine) continue
            val chapterParagraphs = paragraphs.subList(startLine, endLine)
            val startOffset = chapterParagraphs.first().globalStartOffset
            val endOffset = chapterParagraphs.last().globalEndOffset
            val titleCandidate = chapterParagraphs.firstOrNull { it.rawText.isNotBlank() }?.rawText ?: "Глава ${i + 1}"
            val cleanTitle = titleCandidate.replace(Regex("<.*?>"), "").take(60)

            val chunks = buildChunksForChapter(i, chapterParagraphs)

            chapters.add(
                ReaderChapter(
                    chapterIndex = i,
                    title = if (cleanTitle.isNotBlank()) cleanTitle else "Глава ${i + 1}",
                    startOffset = startOffset,
                    endOffset = endOffset,
                    paragraphs = chapterParagraphs,
                    chunks = chunks
                )
            )
        }

        if (chapters.isEmpty()) {
            val fallbackChunks = buildChunksForChapter(0, paragraphs)
            chapters.add(
                ReaderChapter(
                    chapterIndex = 0,
                    title = "Начало книги",
                    startOffset = 0,
                    endOffset = mainText.length,
                    paragraphs = paragraphs,
                    chunks = fallbackChunks
                )
            )
        }

        ReaderDocument(
            bookId = bookId,
            rawMainText = mainText,
            paragraphs = paragraphs,
            chapters = chapters
        )
    }

    private fun buildChunksForChapter(chapterIndex: Int, paragraphs: List<ReaderParagraph>): List<ReaderChunk> {
        val chunks = mutableListOf<ReaderChunk>()
        if (paragraphs.isEmpty()) return chunks

        var currentChunkParas = mutableListOf<ReaderParagraph>()
        var currentChars = 0
        var chunkStart = paragraphs.first().globalStartOffset

        for (para in paragraphs) {
            currentChunkParas.add(para)
            currentChars += para.rawText.length
            if (currentChars >= TARGET_CHUNK_CHAR_COUNT) {
                val chunkEnd = para.globalEndOffset
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
                chunkStart = chunkEnd + 1
            }
        }

        if (currentChunkParas.isNotEmpty()) {
            val chunkEnd = currentChunkParas.last().globalEndOffset
            chunks.add(
                ReaderChunk(
                    chunkIndex = chunks.size,
                    chapterIndex = chapterIndex,
                    startOffset = chunkStart,
                    endOffset = chunkEnd,
                    paragraphs = currentChunkParas.toList()
                )
            )
        }

        return chunks
    }

    private data class TagInfo(val tagName: String, val localStartIndex: Int)

    private fun parseInlines(text: String, lineStart: Int, lineEnd: Int, baseFontSize: androidx.compose.ui.unit.TextUnit): List<ReaderInline> {
        val regex = Regex("<(/?)(b|i|em|s|strike|del|sup|sub|code|title|h1|h2)>", RegexOption.IGNORE_CASE)
        val matches = regex.findAll(text)
        val inlines = mutableListOf<ReaderInline>()
        var currentIndex = 0
        val openTags = mutableListOf<TagInfo>()

        for (match in matches) {
            val matchRange = match.range
            if (matchRange.first > currentIndex) {
                val sub = text.substring(currentIndex, matchRange.first)
                val sOffset = lineStart + currentIndex
                val eOffset = lineStart + matchRange.first
                inlines.add(
                    ReaderInline.Text(
                        content = sub,
                        globalStartOffset = sOffset.coerceAtMost(lineEnd),
                        globalEndOffset = eOffset.coerceAtMost(lineEnd)
                    )
                )
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
                openTags.add(TagInfo(tagName, matchRange.last + 1))
            } else {
                val idx = openTags.indexOfLast { it.tagName == tagName }
                if (idx != -1) {
                    val openTag = openTags.removeAt(idx)
                    val content = text.substring(openTag.localStartIndex, matchRange.first)
                    val spanStyle = getSpanStyle(tagName, baseFontSize)
                    val sOffset = lineStart + openTag.localStartIndex
                    val eOffset = lineStart + matchRange.first
                    if (spanStyle != null) {
                        inlines.add(
                            ReaderInline.Styled(
                                content = content,
                                style = spanStyle,
                                globalStartOffset = sOffset.coerceAtMost(lineEnd),
                                globalEndOffset = eOffset.coerceAtMost(lineEnd)
                            )
                        )
                    } else {
                        inlines.add(
                            ReaderInline.Text(
                                content = content,
                                globalStartOffset = sOffset.coerceAtMost(lineEnd),
                                globalEndOffset = eOffset.coerceAtMost(lineEnd)
                            )
                        )
                    }
                }
            }
            currentIndex = matchRange.last + 1
        }

        if (currentIndex < text.length) {
            val sub = text.substring(currentIndex)
            val sOffset = lineStart + currentIndex
            val eOffset = lineStart + text.length
            inlines.add(
                ReaderInline.Text(
                    content = sub,
                    globalStartOffset = sOffset.coerceAtMost(lineEnd),
                    globalEndOffset = eOffset.coerceAtMost(lineEnd)
                )
            )
        }

        return inlines
    }

    private fun getSpanStyle(tagName: String, baseFontSize: androidx.compose.ui.unit.TextUnit): androidx.compose.ui.text.SpanStyle? {
        return when (tagName) {
            "strong" -> androidx.compose.ui.text.SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            "emphasis" -> androidx.compose.ui.text.SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
            "strikethrough" -> androidx.compose.ui.text.SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
            "sup" -> androidx.compose.ui.text.SpanStyle(baselineShift = androidx.compose.ui.text.style.BaselineShift.Superscript, fontSize = baseFontSize * 0.75f)
            "sub" -> androidx.compose.ui.text.SpanStyle(baselineShift = androidx.compose.ui.text.style.BaselineShift.Subscript, fontSize = baseFontSize * 0.75f)
            "code" -> androidx.compose.ui.text.SpanStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, background = androidx.compose.ui.graphics.Color(0x22888888))
            "chapter" -> androidx.compose.ui.text.SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = baseFontSize * 1.5f)
            else -> null
        }
    }

    private data class AnnotatedMappingResult(
        val annotatedString: AnnotatedString,
        val map: SourceDisplayMap
    )

    private fun buildAnnotatedStringForChunk(chunk: ReaderChunk, baseFontSize: androidx.compose.ui.unit.TextUnit): AnnotatedMappingResult {
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

    suspend fun paginate(
        context: Context,
        document: ReaderDocument,
        config: ReaderConfiguration,
        viewport: ReaderViewport,
        textMeasurer: TextMeasurer,
        initialTargetOffset: Int = 0,
        onPagesUpdated: (List<ReaderPage>, Boolean) -> Unit
    ): List<ReaderPage> = withContext(Dispatchers.Default) {
        if (document.chapters.isEmpty() || config.maxWidthPx <= 0 || config.maxHeightPx <= 0) return@withContext emptyList()

        val layoutKey = buildLayoutKey(document.bookId, config)
        val textStyle = TextStyle(
            fontSize = config.fontSize,
            fontFamily = config.fontFamily,
            fontWeight = config.fontWeight,
            textAlign = TextAlign.Justify,
            lineHeight = (config.fontSize.value * config.lineSpacing).sp,
            letterSpacing = 0.1.sp,
            lineBreak = LineBreak.Paragraph,
            hyphens = Hyphens.Auto,
            platformStyle = PlatformTextStyle(includeFontPadding = false)
        )

        val safeMaxHeightPx = config.maxHeightPx
        val maxWidthPx = config.maxWidthPx
        val chapters = document.chapters

        var initialChapterIdx = 0
        var initialChunkIdx = 0
        outer@ for ((chIdx, ch) in chapters.withIndex()) {
            if (initialTargetOffset in ch.startOffset..ch.endOffset) {
                initialChapterIdx = chIdx
                for ((chunkIdx, chunk) in ch.chunks.withIndex()) {
                    if (initialTargetOffset in chunk.startOffset..chunk.endOffset) {
                        initialChunkIdx = chunkIdx
                        break@outer
                    }
                }
                break
            }
        }

        val pageIndexBuilder = ReaderPageIndexBuilder()

        // Priority 0: Paginate initial chunk immediately for lightning-fast first display
        val initialChapter = chapters[initialChapterIdx]
        val initialChunk = initialChapter.chunks.getOrElse(initialChunkIdx) { initialChapter.chunks.first() }

        val initialPages = paginateChunk(
            context = context,
            bookId = document.bookId,
            layoutKey = layoutKey,
            chapterIndex = initialChapterIdx,
            chunk = initialChunk,
            textMeasurer = textMeasurer,
            textStyle = textStyle,
            maxWidthPx = maxWidthPx,
            safeMaxHeightPx = safeMaxHeightPx
        )

        pageIndexBuilder.addPagesForChunk(initialChapterIdx, initialChunk.chunkIndex, initialPages)
        var currentSnapshot = pageIndexBuilder.buildSnapshot()
        onPagesUpdated(currentSnapshot.allEntries().map { it.toReaderPage() }, true)
        yield()

        // Background priority pagination queue
        val taskQueue = PriorityQueue<PaginationTask>()
        val processedChunks = mutableSetOf<Pair<Int, Int>>()
        processedChunks.add(initialChapterIdx to initialChunk.chunkIndex)

        // Queue initial chapter remaining chunks (NEARBY)
        for (chunk in initialChapter.chunks) {
            if (chunk.chunkIndex != initialChunk.chunkIndex) {
                taskQueue.add(PaginationTask(initialChapterIdx, chunk.chunkIndex, PaginationPriority.NEARBY, initialTargetOffset))
            }
        }

        // Queue other chapters (BACKGROUND)
        for ((chIdx, ch) in chapters.withIndex()) {
            if (chIdx != initialChapterIdx) {
                for (chunk in ch.chunks) {
                    taskQueue.add(PaginationTask(chIdx, chunk.chunkIndex, PaginationPriority.BACKGROUND, ch.startOffset))
                }
            }
        }

        while (taskQueue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val task = taskQueue.poll() ?: break
            val key = task.chapterIndex to task.chunkIndex
            if (processedChunks.contains(key)) continue
            processedChunks.add(key)

            val chapter = chapters[task.chapterIndex]
            val chunk = chapter.chunks.getOrNull(task.chunkIndex) ?: continue

            val cachedPages = PaginationDiskCache.getChapterChunkPages(context, document.bookId, layoutKey, task.chapterIndex, task.chunkIndex) // or use existing DiskCache
            val chunkPages = if (cachedPages != null) {
                cachedPages
            } else {
                paginateChunk(
                    context = context,
                    bookId = document.bookId,
                    layoutKey = layoutKey,
                    chapterIndex = task.chapterIndex,
                    chunk = chunk,
                    textMeasurer = textMeasurer,
                    textStyle = textStyle,
                    maxWidthPx = maxWidthPx,
                    safeMaxHeightPx = safeMaxHeightPx
                )
            }

            pageIndexBuilder.addPagesForChunk(task.chapterIndex, task.chunkIndex, chunkPages)
            currentSnapshot = pageIndexBuilder.buildSnapshot()
            onPagesUpdated(currentSnapshot.allEntries().map { it.toReaderPage() }, false)
            yield()
        }

        val finalPages = currentSnapshot.allEntries().map { it.toReaderPage() }
        validatePagination(finalPages, safeMaxHeightPx)
        finalPages
    }

    private fun buildLayoutKey(bookId: String, config: ReaderConfiguration): String {
        return "${bookId}_${config.fontFamily.hashCode()}_${config.fontSize.value}_${config.fontWeight.weight}_${config.lineSpacing}_${config.maxWidthPx}_${config.maxHeightPx}"
    }

    private suspend fun paginateChunk(
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
            val startOffset = pageLines.first().startOffset
            val endOffset = pageLines.last().endOffset

            val startAnnotated = if (startOffset in map.sourceToDisplayStart.indices && map.sourceToDisplayStart[startOffset] != -1) {
                map.sourceToDisplayStart[startOffset]
            } else {
                0
            }
            val endAnnotated = if (endOffset in map.sourceToDisplayEnd.indices && map.sourceToDisplayEnd[endOffset] != -1) {
                map.sourceToDisplayEnd[endOffset]
            } else {
                annotated.length
            }

            val slice = if (startAnnotated < endAnnotated && endAnnotated <= annotated.length) {
                annotated.subSequence(startAnnotated, endAnnotated).trimTrailingWhitespace()
            } else {
                annotated
            }

            if (slice.isNotEmpty() || startOffset < endOffset) {
                chunkPages.add(
                    ReaderPage(
                        pageIndex = localPageIndex++,
                        text = slice,
                        startOffset = startOffset,
                        endOffset = endOffset
                    )
                )
            }

            pageStartLineIdx = pageEndLineIdx
        }

        return chunkPages
    }

    private fun AnnotatedString.trimTrailingWhitespace(): AnnotatedString {
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
