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

object ReaderLayoutEngine {

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

            chapters.add(
                ReaderChapter(
                    chapterIndex = i,
                    title = if (cleanTitle.isNotBlank()) cleanTitle else "Глава ${i + 1}",
                    startOffset = startOffset,
                    endOffset = endOffset,
                    paragraphs = chapterParagraphs
                )
            )
        }

        if (chapters.isEmpty()) {
            chapters.add(
                ReaderChapter(
                    chapterIndex = 0,
                    title = "Начало книги",
                    startOffset = 0,
                    endOffset = mainText.length,
                    paragraphs = paragraphs
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
        val sourceOffsets: IntArray
    )

    private fun buildAnnotatedStringForChapter(chapter: ReaderChapter, baseFontSize: androidx.compose.ui.unit.TextUnit): AnnotatedMappingResult {
        val sourceOffsetList = mutableListOf<Int>()
        val annotated = androidx.compose.ui.text.buildAnnotatedString {
            for ((index, paragraph) in chapter.paragraphs.withIndex()) {
                if (index > 0) {
                    append("\n")
                    val newlineOffset = (paragraph.globalStartOffset - 1).coerceAtLeast(chapter.startOffset)
                    sourceOffsetList.add(newlineOffset)
                }
                for (inline in paragraph.inlines) {
                    when (inline) {
                        is ReaderInline.Text -> {
                            val content = inline.content
                            append(content)
                            val startOffset = inline.globalStartOffset
                            for (i in content.indices) {
                                sourceOffsetList.add(startOffset + i)
                            }
                        }
                        is ReaderInline.Styled -> {
                            val content = inline.content
                            val startAnnotatedIndex = length
                            append(content)
                            addStyle(inline.style, startAnnotatedIndex, length)
                            val startOffset = inline.globalStartOffset
                            for (i in content.indices) {
                                sourceOffsetList.add(startOffset + i)
                            }
                        }
                    }
                }
            }
        }
        val sourceOffsets = sourceOffsetList.toIntArray()
        require(sourceOffsets.size == annotated.length) {
            "Offset mapping size (${sourceOffsets.size}) must match annotatedString length (${annotated.length})"
        }
        return AnnotatedMappingResult(annotated, sourceOffsets)
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

        val layoutKey = "${config.fontFamily.hashCode()}_${config.fontSize.value}_${config.fontWeight.weight}_${config.lineSpacing}_${config.maxWidthPx}_${config.maxHeightPx}"
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
        for ((idx, ch) in chapters.withIndex()) {
            if (initialTargetOffset in ch.startOffset..ch.endOffset) {
                initialChapterIdx = idx
                break
            }
        }

        val chapterPagesMap = mutableMapOf<Int, List<ReaderPage>>()

        // Priority 0: Paginate initial chapter immediately
        val initialChapter = chapters[initialChapterIdx]
        val initialPages = paginateChapter(
            context = context,
            bookId = document.bookId,
            layoutKey = layoutKey,
            chapter = initialChapter,
            textMeasurer = textMeasurer,
            textStyle = textStyle,
            maxWidthPx = maxWidthPx,
            safeMaxHeightPx = safeMaxHeightPx
        )
        chapterPagesMap[initialChapterIdx] = initialPages

        var allPages = assembleAllPages(chapters, chapterPagesMap)
        onPagesUpdated(allPages, true)

        // Priority background pagination for remaining chapters
        val priorityQueue = mutableListOf<Int>()
        for (i in (initialChapterIdx + 1) until chapters.size) priorityQueue.add(i)
        for (i in (initialChapterIdx - 1) downTo 0) priorityQueue.add(i)

        for (chIdx in priorityQueue) {
            currentCoroutineContext().ensureActive()
            if (chapterPagesMap.containsKey(chIdx)) continue

            val chapter = chapters[chIdx]
            val pages = paginateChapter(
                context = context,
                bookId = document.bookId,
                layoutKey = layoutKey,
                chapter = chapter,
                textMeasurer = textMeasurer,
                textStyle = textStyle,
                maxWidthPx = maxWidthPx,
                safeMaxHeightPx = safeMaxHeightPx
            )
            chapterPagesMap[chIdx] = pages
            allPages = assembleAllPages(chapters, chapterPagesMap)
            onPagesUpdated(allPages, false)
            yield()
        }

        validatePagination(allPages, safeMaxHeightPx)
        allPages
    }

    private suspend fun paginateChapter(
        context: Context,
        bookId: String,
        layoutKey: String,
        chapter: ReaderChapter,
        textMeasurer: TextMeasurer,
        textStyle: TextStyle,
        maxWidthPx: Int,
        safeMaxHeightPx: Int
    ): List<ReaderPage> {
        val cachedOffsets = PaginationDiskCache.getChapterOffsets(context, bookId, layoutKey, chapter.chapterIndex)
        val mappingResult = buildAnnotatedStringForChapter(chapter, textStyle.fontSize)
        val annotated = mappingResult.annotatedString
        val offsetMapping = mappingResult.sourceOffsets

        if (cachedOffsets != null && cachedOffsets.isNotEmpty()) {
            val pages = mutableListOf<ReaderPage>()
            for ((pIdx, pair) in cachedOffsets.withIndex()) {
                val start = pair.first
                val end = pair.second
                val startAnnotated = findAnnotatedIndexForOffset(offsetMapping, start, annotated.length)
                val endAnnotated = findAnnotatedIndexForOffset(offsetMapping, end, annotated.length, roundUp = true)
                if (startAnnotated < endAnnotated && endAnnotated <= annotated.length) {
                    val slice = annotated.subSequence(startAnnotated, endAnnotated).trimTrailingWhitespace()
                    if (slice.isNotEmpty()) {
                        pages.add(
                            ReaderPage(
                                pageIndex = pages.size,
                                text = slice,
                                startOffset = start,
                                endOffset = end
                            )
                        )
                    }
                }
            }
            if (pages.isNotEmpty()) return pages
        }

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

            val startSource = if (lineStartChar < offsetMapping.size) offsetMapping[lineStartChar] else chapter.startOffset
            val endSource = if (lineEndChar > 0 && lineEndChar - 1 < offsetMapping.size) {
                offsetMapping[lineEndChar - 1] + 1
            } else {
                chapter.endOffset
            }

            lines.add(
                LayoutLine(
                    startOffset = startSource,
                    endOffset = endSource.coerceAtMost(chapter.endOffset),
                    top = top,
                    bottom = bottom,
                    height = height
                )
            )
        }

        if (lines.isEmpty()) return emptyList()

        val chapterPages = mutableListOf<ReaderPage>()
        val computedPairs = mutableListOf<Pair<Int, Int>>()
        var pageStartLineIdx = 0
        var localPageIndex = 0

        while (pageStartLineIdx < lines.size) {
            val pageTop = lines[pageStartLineIdx].top
            var pageEndLineIdx = pageStartLineIdx

            while (pageEndLineIdx < lines.size) {
                val candidateLine = lines[pageEndLineIdx]
                val pageHeight = candidateLine.bottom - pageTop
                if (pageHeight <= safeMaxHeightPx || pageEndLineIdx == pageStartLineIdx) {
                    pageEndLineIdx++
                } else {
                    break
                }
            }

            val pageLines = lines.subList(pageStartLineIdx, pageEndLineIdx)
            val startOffset = pageLines.first().startOffset
            val endOffset = pageLines.last().endOffset

            val startAnnotated = findAnnotatedIndexForOffset(offsetMapping, startOffset, annotated.length)
            val endAnnotated = findAnnotatedIndexForOffset(offsetMapping, endOffset, annotated.length, roundUp = true)

            val slice = if (startAnnotated < endAnnotated && endAnnotated <= annotated.length) {
                annotated.subSequence(startAnnotated, endAnnotated).trimTrailingWhitespace()
            } else {
                annotated.subSequence(
                    findAnnotatedIndexForOffset(offsetMapping, startOffset, annotated.length),
                    annotated.length
                )
            }

            if (slice.isNotEmpty()) {
                chapterPages.add(
                    ReaderPage(
                        pageIndex = localPageIndex++,
                        text = slice,
                        startOffset = startOffset,
                        endOffset = endOffset
                    )
                )
                computedPairs.add(startOffset to endOffset)
            }

            pageStartLineIdx = pageEndLineIdx
        }

        if (computedPairs.isNotEmpty()) {
            PaginationDiskCache.saveChapterOffsets(context, bookId, layoutKey, chapter.chapterIndex, computedPairs)
        }

        return chapterPages
    }

    private fun assembleAllPages(chapters: List<ReaderChapter>, chapterPagesMap: Map<Int, List<ReaderPage>>): List<ReaderPage> {
        val result = mutableListOf<ReaderPage>()
        var globalPageIndex = 0
        for (i in chapters.indices) {
            val pages = chapterPagesMap[i] ?: continue
            for (p in pages) {
                result.add(p.copy(pageIndex = globalPageIndex++))
            }
        }
        return result
    }

    private fun findAnnotatedIndexForOffset(offsetMapping: IntArray, targetOffset: Int, maxAnnotatedLength: Int, roundUp: Boolean = false): Int {
        var low = 0
        var high = offsetMapping.size - 1
        var best = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (offsetMapping[mid] <= targetOffset) {
                best = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return if (roundUp) (best + 1).coerceAtMost(maxAnnotatedLength) else best.coerceAtMost(maxAnnotatedLength)
    }

    private fun AnnotatedString.trimTrailingWhitespace(): AnnotatedString {
        var end = length
        while (end > 0 && text[end - 1].isWhitespace()) {
            end--
        }
        return if (end == length) this else subSequence(0, end)
    }

    fun validatePagination(pages: List<ReaderPage>, maxHeightPx: Int) {
        for (i in pages.indices) {
            val page = pages[i]
            if (i > 0) {
                val prev = pages[i - 1]
                if (prev.endOffset > page.startOffset) {
                    Log.w("ReaderValidation", "OVERLAP between page ${prev.pageIndex} (ends ${prev.endOffset}) and page ${page.pageIndex} (starts ${page.startOffset})")
                }
            }
        }
        Log.d("ReaderValidation", "Pagination validation completed for ${pages.size} pages.")
    }
}
