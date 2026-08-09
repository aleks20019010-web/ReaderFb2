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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

object ReaderLayoutEngine {

    enum class PaginationPriority {
        CURRENT,
        NEARBY,
        BACKWARD,
        BACKGROUND
    }

    suspend fun parseDocument(bookId: String, mainText: String, baseFontSize: androidx.compose.ui.unit.TextUnit): ReaderDocument = withContext(Dispatchers.Default) {
        val paragraphs = mutableListOf<ReaderParagraph>()
        val chapters = mutableListOf<ReaderChapter>()
        val lines = mainText.split('\n')
        var currentGlobalOffset = 0

        val chapterIndices = mutableListOf<Int>()
        chapterIndices.add(0) // First chapter always starts at 0

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

        // Build chapters from chapterIndices
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

    private fun buildAnnotatedStringForDocument(document: ReaderDocument, baseFontSize: androidx.compose.ui.unit.TextUnit): AnnotatedMappingResult {
        val sourceOffsetList = mutableListOf<Int>()
        val annotated = androidx.compose.ui.text.buildAnnotatedString {
            for ((index, paragraph) in document.paragraphs.withIndex()) {
                if (index > 0) {
                    append("\n")
                    val newlineOffset = (paragraph.globalStartOffset - 1).coerceIn(0, document.rawMainText.length)
                    sourceOffsetList.add(newlineOffset)
                }
                for (inline in paragraph.inlines) {
                    when (inline) {
                        is ReaderInline.Text -> {
                            val content = inline.content
                            append(content)
                            val startOffset = inline.globalStartOffset
                            for (i in content.indices) {
                                sourceOffsetList.add((startOffset + i).coerceIn(0, document.rawMainText.length))
                            }
                        }
                        is ReaderInline.Styled -> {
                            val content = inline.content
                            val startAnnotatedIndex = length
                            append(content)
                            addStyle(inline.style, startAnnotatedIndex, length)
                            val startOffset = inline.globalStartOffset
                            for (i in content.indices) {
                                sourceOffsetList.add((startOffset + i).coerceIn(0, document.rawMainText.length))
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
        if (document.paragraphs.isEmpty() || config.maxWidthPx <= 0 || config.maxHeightPx <= 0) return@withContext emptyList()

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

        val mappingResult = buildAnnotatedStringForDocument(document, config.fontSize)
        val docAnnotated = mappingResult.annotatedString
        val offsetMapping = mappingResult.sourceOffsets
        val docLength = docAnnotated.length
        if (docLength == 0) return@withContext emptyList()

        val chapters = document.chapters
        val chapterPagesMap = mutableMapOf<Int, List<ReaderPage>>()
        
        // Find chapter containing initialTargetOffset
        var initialChapterIdx = 0
        for ((idx, ch) in chapters.withIndex()) {
            if (initialTargetOffset in ch.startOffset..ch.endOffset) {
                initialChapterIdx = idx
                break
            }
        }

        // 1. Paginate initial chapter first (Priority 1)
        val initialChapter = chapters[initialChapterIdx]
        val initialPages = paginateChapter(
            context = context,
            bookId = document.bookId,
            layoutKey = layoutKey,
            chapter = initialChapter,
            docAnnotated = docAnnotated,
            offsetMapping = offsetMapping,
            rawMainText = document.rawMainText,
            textMeasurer = textMeasurer,
            textStyle = textStyle,
            maxWidthPx = maxWidthPx,
            safeMaxHeightPx = safeMaxHeightPx
        )
        chapterPagesMap[initialChapterIdx] = initialPages

        // Combine and emit immediately
        var allPages = assembleAllPages(chapters, chapterPagesMap)
        onPagesUpdated(allPages, true)

        // 2. Background pagination for remaining chapters in priority order
        val priorityQueue = mutableListOf<Int>()
        // Priority 1: chapters ahead
        for (i in (initialChapterIdx + 1) until chapters.size) priorityQueue.add(i)
        // Priority 2: chapters behind
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
                docAnnotated = docAnnotated,
                offsetMapping = offsetMapping,
                rawMainText = document.rawMainText,
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

        allPages
    }

    private suspend fun paginateChapter(
        context: Context,
        bookId: String,
        layoutKey: String,
        chapter: ReaderChapter,
        docAnnotated: AnnotatedString,
        offsetMapping: IntArray,
        rawMainText: String,
        textMeasurer: TextMeasurer,
        textStyle: TextStyle,
        maxWidthPx: Int,
        safeMaxHeightPx: Int
    ): List<ReaderPage> {
        // Check cache first
        val cachedOffsets = PaginationDiskCache.getChapterOffsets(context, bookId, layoutKey, chapter.chapterIndex)
        if (cachedOffsets != null && cachedOffsets.isNotEmpty()) {
            val pages = mutableListOf<ReaderPage>()
            for ((pIdx, pair) in cachedOffsets.withIndex()) {
                val start = pair.first
                val end = pair.second
                // Find annotated indices corresponding to start and end
                val annotatedStartIndex = findAnnotatedIndexForOffset(offsetMapping, start, docAnnotated.length)
                val annotatedEndIndex = findAnnotatedIndexForOffset(offsetMapping, end, docAnnotated.length)
                if (annotatedStartIndex < annotatedEndIndex && annotatedEndIndex <= docAnnotated.length) {
                    val slice = docAnnotated.subSequence(annotatedStartIndex, annotatedEndIndex).trimTrailingWhitespace()
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
            if (pages.isNotEmpty()) {
                return pages
            }
        }

        // Find annotated start and end indices for chapter
        val chapterStartAnnotated = findAnnotatedIndexForOffset(offsetMapping, chapter.startOffset, docAnnotated.length)
        val chapterEndAnnotated = findAnnotatedIndexForOffset(offsetMapping, chapter.endOffset, docAnnotated.length, roundUp = true)

        val chapterPages = mutableListOf<ReaderPage>()
        var startIndex = chapterStartAnnotated.coerceIn(0, docAnnotated.length)
        val endIndex = chapterEndAnnotated.coerceIn(startIndex, docAnnotated.length)
        var localPageIndex = 0

        val computedPairs = mutableListOf<Pair<Int, Int>>()

        while (startIndex < endIndex) {
            currentCoroutineContext().ensureActive()
            while (startIndex < endIndex && docAnnotated[startIndex].isWhitespace() && docAnnotated[startIndex] != '\n') {
                startIndex++
            }
            if (startIndex >= endIndex) break

            val fittingEnd = findLargestFittingEnd(
                annotatedDoc = docAnnotated,
                start = startIndex,
                endLimit = endIndex,
                textMeasurer = textMeasurer,
                textStyle = textStyle,
                maxWidth = maxWidthPx,
                maxHeight = safeMaxHeightPx
            )

            val refinedEnd = refineEndIndex(
                annotatedDoc = docAnnotated,
                start = startIndex,
                endLimit = endIndex,
                initialBest = fittingEnd,
                textMeasurer = textMeasurer,
                textStyle = textStyle,
                maxWidth = maxWidthPx,
                maxHeight = safeMaxHeightPx
            )

            val rawSlice = docAnnotated.subSequence(startIndex, refinedEnd)
            val candidateText = rawSlice.trimTrailingWhitespace()
            val displayEnd = startIndex + candidateText.length
            val consumedEnd = refinedEnd

            if (candidateText.isEmpty()) {
                startIndex = (startIndex + 1).coerceAtMost(endIndex)
                continue
            }

            val startMainOffset = if (startIndex < offsetMapping.size) offsetMapping[startIndex] else chapter.startOffset
            val endMainOffset = if (displayEnd < offsetMapping.size) {
                offsetMapping[displayEnd]
            } else if (displayEnd - 1 < offsetMapping.size) {
                offsetMapping[displayEnd - 1] + 1
            } else {
                chapter.endOffset
            }

            val page = ReaderPage(
                pageIndex = localPageIndex++,
                text = candidateText,
                startOffset = startMainOffset,
                endOffset = endMainOffset.coerceAtMost(rawMainText.length)
            )
            chapterPages.add(page)
            computedPairs.add(startMainOffset to endMainOffset.coerceAtMost(rawMainText.length))

            startIndex = consumedEnd.coerceAtLeast(startIndex + 1)
        }

        // Save to cache
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

    private fun findLargestFittingEnd(
        annotatedDoc: AnnotatedString,
        start: Int,
        endLimit: Int,
        textMeasurer: TextMeasurer,
        textStyle: TextStyle,
        maxWidth: Int,
        maxHeight: Int
    ): Int {
        var low = (start + 1).coerceAtMost(endLimit)
        var high = endLimit
        var best = low

        while (low <= high) {
            val mid = (low + high) ushr 1
            val slice = annotatedDoc.subSequence(start, mid).trimTrailingWhitespace()
            if (slice.isEmpty()) {
                low = mid + 1
                continue
            }
            val layout = textMeasurer.measure(
                text = slice,
                style = textStyle,
                constraints = Constraints(maxWidth = maxWidth)
            )
            val fits = layout.size.height <= maxHeight && !layout.didOverflowHeight
            if (fits) {
                best = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return best
    }

    private fun refineEndIndex(
        annotatedDoc: AnnotatedString,
        start: Int,
        endLimit: Int,
        initialBest: Int,
        textMeasurer: TextMeasurer,
        textStyle: TextStyle,
        maxWidth: Int,
        maxHeight: Int
    ): Int {
        var target = initialBest.coerceAtMost(endLimit)
        for (i in target downTo (start + 1)) {
            val char = annotatedDoc[i - 1]
            if (char == ' ' || char == '\n' || char == '-' || char == '\t' || char == '.' || char == ',' || char == ';') {
                val slice = annotatedDoc.subSequence(start, i).trimTrailingWhitespace()
                if (slice.isNotEmpty()) {
                    val layout = textMeasurer.measure(
                        text = slice,
                        style = textStyle,
                        constraints = Constraints(maxWidth = maxWidth)
                    )
                    if (layout.size.height <= maxHeight && !layout.didOverflowHeight) {
                        target = i
                        break
                    }
                }
            }
        }
        val finalSlice = annotatedDoc.subSequence(start, target).trimTrailingWhitespace()
        if (finalSlice.isNotEmpty()) {
            val layout = textMeasurer.measure(
                text = finalSlice,
                style = textStyle,
                constraints = Constraints(maxWidth = maxWidth)
            )
            if (layout.size.height <= maxHeight && !layout.didOverflowHeight) {
                return target
            }
        }
        return initialBest.coerceAtMost(endLimit)
    }

    private fun AnnotatedString.trimTrailingWhitespace(): AnnotatedString {
        var end = length
        while (end > 0 && text[end - 1].isWhitespace()) {
            end--
        }
        return if (end == length) this else subSequence(0, end)
    }
}
