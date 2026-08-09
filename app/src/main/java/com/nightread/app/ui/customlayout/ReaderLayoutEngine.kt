package com.nightread.app.ui.customlayout

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

object ReaderLayoutEngine {

    suspend fun parseDocument(bookId: String, mainText: String, baseFontSize: androidx.compose.ui.unit.TextUnit): ReaderDocument = withContext(Dispatchers.Default) {
        val paragraphs = mutableListOf<ReaderParagraph>()
        val lines = mainText.split('\n')
        var currentGlobalOffset = 0

        for (line in lines) {
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

        ReaderDocument(
            bookId = bookId,
            rawMainText = mainText,
            paragraphs = paragraphs
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
        for (i in 0 until sourceOffsets.size - 1) {
            require(sourceOffsets[i] <= sourceOffsets[i + 1]) {
                "Offset mapping must be monotonic at index $i: ${sourceOffsets[i]} > ${sourceOffsets[i + 1]}"
            }
        }
        return AnnotatedMappingResult(annotated, sourceOffsets)
    }

    suspend fun paginate(
        document: ReaderDocument,
        config: ReaderConfiguration,
        viewport: ReaderViewport,
        textMeasurer: TextMeasurer,
        onPagesUpdated: (List<ReaderPage>, Boolean) -> Unit
    ): List<ReaderPage> = withContext(Dispatchers.Default) {
        if (document.paragraphs.isEmpty() || config.maxWidthPx <= 0 || config.maxHeightPx <= 0) return@withContext emptyList()

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

        val accumulatedPages = mutableListOf<ReaderPage>()
        var pageIndex = 0
        var startIndex = 0
        var isFirstPage = true

        while (startIndex < docLength) {
            // 1. Skip leading whitespace / newlines if any
            while (startIndex < docLength && docAnnotated[startIndex].isWhitespace() && docAnnotated[startIndex] != '\n') {
                startIndex++
            }
            if (startIndex >= docLength) break

            // 2. Binary search for fittingEnd (fitting max index <= maxHeight)
            val fittingEnd = findLargestFittingEnd(
                annotatedDoc = docAnnotated,
                start = startIndex,
                textMeasurer = textMeasurer,
                textStyle = textStyle,
                maxWidth = maxWidthPx,
                maxHeight = safeMaxHeightPx
            )

            // 3. Refine word boundary (backwards search from fittingEnd to start + 1)
            val refinedEnd = refineEndIndex(
                annotatedDoc = docAnnotated,
                start = startIndex,
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
                // Force advance by at least 1 character to prevent infinite loop
                startIndex = (startIndex + 1).coerceAtMost(docLength)
                continue
            }

            // Final measurement assertion
            val finalLayout = textMeasurer.measure(
                text = candidateText,
                style = textStyle,
                constraints = Constraints(maxWidth = maxWidthPx)
            )

            val actualHeight = finalLayout.size.height.toFloat()
            if (finalLayout.didOverflowHeight || actualHeight > safeMaxHeightPx.toFloat()) {
                Log.e("ReaderLayoutEngine", "OVERFLOW DETECTED: height $actualHeight > max $safeMaxHeightPx or didOverflowHeight. Forcing 1 char fallback.")
                val fallbackEnd = (startIndex + 1).coerceAtMost(docLength)
                val fallbackSlice = docAnnotated.subSequence(startIndex, fallbackEnd).trimTrailingWhitespace()
                if (fallbackSlice.isNotEmpty()) {
                    val startMainOffset = if (startIndex < offsetMapping.size) offsetMapping[startIndex] else 0
                    val endMainOffset = if (fallbackEnd < offsetMapping.size) offsetMapping[fallbackEnd] else document.rawMainText.length

                    val page = ReaderPage(
                        pageIndex = pageIndex++,
                        text = fallbackSlice,
                        startOffset = startMainOffset,
                        endOffset = endMainOffset
                    )
                    accumulatedPages.add(page)
                    startIndex = fallbackEnd
                    continue
                } else {
                    startIndex++
                    continue
                }
            }

            val startMainOffset = if (startIndex < offsetMapping.size) offsetMapping[startIndex] else 0
            val endMainOffset = if (displayEnd < offsetMapping.size) {
                offsetMapping[displayEnd]
            } else if (displayEnd - 1 < offsetMapping.size) {
                offsetMapping[displayEnd - 1] + 1
            } else {
                document.rawMainText.length
            }

            val page = ReaderPage(
                pageIndex = pageIndex++,
                text = candidateText,
                startOffset = startMainOffset,
                endOffset = endMainOffset.coerceAtMost(document.rawMainText.length)
            )

            Log.d("ReaderLayoutEngine", "Page OK: index ${page.pageIndex}, height $actualHeight / $safeMaxHeightPx, chars ${candidateText.length}")

            accumulatedPages.add(page)

            if (isFirstPage) {
                onPagesUpdated(accumulatedPages.toList(), true)
                isFirstPage = false
                yield()
            } else if (accumulatedPages.size % 10 == 0) {
                onPagesUpdated(accumulatedPages.toList(), false)
                yield()
            }

            // Advance startIndex to consumedEnd to ensure no text loss or duplication
            startIndex = consumedEnd.coerceAtLeast(startIndex + 1)
        }

        if (accumulatedPages.isNotEmpty()) {
            onPagesUpdated(accumulatedPages.toList(), false)
        }
        return@withContext accumulatedPages.toList()
    }

    private fun findLargestFittingEnd(
        annotatedDoc: AnnotatedString,
        start: Int,
        textMeasurer: TextMeasurer,
        textStyle: TextStyle,
        maxWidth: Int,
        maxHeight: Int
    ): Int {
        var low = (start + 1).coerceAtMost(annotatedDoc.length)
        var high = annotatedDoc.length
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
        initialBest: Int,
        textMeasurer: TextMeasurer,
        textStyle: TextStyle,
        maxWidth: Int,
        maxHeight: Int
    ): Int {
        var target = initialBest
        for (i in initialBest downTo (start + 1)) {
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
        return initialBest
    }

    private fun AnnotatedString.trimTrailingWhitespace(): AnnotatedString {
        var end = length
        while (end > 0 && text[end - 1].isWhitespace()) {
            end--
        }
        return if (end == length) this else subSequence(0, end)
    }
}
