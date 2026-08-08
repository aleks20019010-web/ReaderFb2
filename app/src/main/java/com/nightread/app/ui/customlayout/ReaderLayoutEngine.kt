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
        val formattedText = com.nightread.app.utils.TypographyUtils.applyMicroTypography(mainText)
        val rawSections = formattedText.split('\u000C')
        val paragraphs = mutableListOf<ReaderParagraph>()
        var currentGlobalOffset = 0

        for (section in rawSections) {
            val sectionTrimmed = section.trim()
            if (sectionTrimmed.isEmpty()) {
                currentGlobalOffset += section.length + 1
                continue
            }

            val lines = sectionTrimmed.split('\n')
            for (line in lines) {
                val lineStart = currentGlobalOffset
                val lineEnd = lineStart + line.length
                val inlines = parseInlines(line, lineStart, baseFontSize)
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
        }

        ReaderDocument(
            bookId = bookId,
            rawMainText = mainText,
            paragraphs = paragraphs
        )
    }

    private data class TagInfo(val tagName: String, val localStartIndex: Int)

    private fun parseInlines(text: String, baseOffset: Int, baseFontSize: androidx.compose.ui.unit.TextUnit): List<ReaderInline> {
        val regex = Regex("<(/?)(b|i|em|s|strike|del|sup|sub|code|title|h1|h2)>", RegexOption.IGNORE_CASE)
        val matches = regex.findAll(text)
        val inlines = mutableListOf<ReaderInline>()
        var currentIndex = 0
        val openTags = mutableListOf<TagInfo>()

        for (match in matches) {
            val matchRange = match.range
            if (matchRange.first > currentIndex) {
                val sub = text.substring(currentIndex, matchRange.first)
                inlines.add(ReaderInline.Text(sub, baseOffset + currentIndex, baseOffset + matchRange.first))
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
                    if (spanStyle != null) {
                        inlines.add(ReaderInline.Styled(content, spanStyle, baseOffset + openTag.localStartIndex, baseOffset + matchRange.first))
                    } else {
                        inlines.add(ReaderInline.Text(content, baseOffset + openTag.localStartIndex, baseOffset + matchRange.first))
                    }
                }
            }
            currentIndex = matchRange.last + 1
        }

        if (currentIndex < text.length) {
            val sub = text.substring(currentIndex)
            inlines.add(ReaderInline.Text(sub, baseOffset + currentIndex, baseOffset + text.length))
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
        val offsetMapping: IntArray
    )

    private fun buildAnnotatedStringForDocument(document: ReaderDocument, baseFontSize: androidx.compose.ui.unit.TextUnit): AnnotatedMappingResult {
        val mappingList = mutableListOf<Int>()
        val annotated = androidx.compose.ui.text.buildAnnotatedString {
            for ((index, paragraph) in document.paragraphs.withIndex()) {
                if (index > 0) {
                    append("\n")
                    val fallbackOffset = if (index > 0 && document.paragraphs.indices.contains(index - 1)) {
                        document.paragraphs[index - 1].globalEndOffset
                    } else {
                        paragraph.globalStartOffset
                    }
                    mappingList.add(fallbackOffset)
                }
                for (inline in paragraph.inlines) {
                    when (inline) {
                        is ReaderInline.Text -> {
                            val content = inline.content
                            val startOffset = inline.globalStartOffset
                            append(content)
                            for (cIdx in content.indices) {
                                mappingList.add((startOffset + cIdx).coerceIn(0, document.rawMainText.length))
                            }
                        }
                        is ReaderInline.Styled -> {
                            val content = inline.content
                            val startOffset = inline.globalStartOffset
                            val startAnnotatedIndex = length
                            append(content)
                            addStyle(inline.style, startAnnotatedIndex, length)
                            for (cIdx in content.indices) {
                                mappingList.add((startOffset + cIdx).coerceIn(0, document.rawMainText.length))
                            }
                        }
                    }
                }
            }
        }
        return AnnotatedMappingResult(annotated, mappingList.toIntArray())
    }

    private fun mapAnnotatedIndexToGlobalOffset(annotatedIndex: Int, mapping: IntArray, maxMainTextLength: Int): Int {
        if (mapping.isEmpty()) return 0
        if (annotatedIndex <= 0) return mapping[0]
        if (annotatedIndex >= mapping.size) return mapping[mapping.size - 1]
        return mapping[annotatedIndex].coerceIn(0, maxMainTextLength)
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

        val mappingResult = buildAnnotatedStringForDocument(document, config.fontSize)
        val docAnnotated = mappingResult.annotatedString
        val offsetMapping = mappingResult.offsetMapping
        if (docAnnotated.isEmpty()) return@withContext emptyList()

        val layoutResult = textMeasurer.measure(
            text = docAnnotated,
            style = textStyle,
            constraints = Constraints(maxWidth = config.maxWidthPx)
        )

        val lineCount = layoutResult.lineCount
        if (lineCount == 0) return@withContext emptyList()

        val accumulatedPages = mutableListOf<ReaderPage>()
        var pageIndex = 0
        var currentLine = 0
        var isFirstPage = true

        while (currentLine < lineCount) {
            val pageTop = layoutResult.getLineTop(currentLine)
            var candidateEndLine = currentLine
            while (candidateEndLine < lineCount) {
                val bottom = layoutResult.getLineBottom(candidateEndLine)
                if ((bottom - pageTop) <= safeMaxHeightPx) {
                    candidateEndLine++
                } else {
                    break
                }
            }
            if (candidateEndLine <= currentLine) {
                candidateEndLine = currentLine
            } else {
                candidateEndLine = candidateEndLine - 1
            }

            var bestEndLine = candidateEndLine
            var bestPageAnnotated: AnnotatedString? = null
            var bestStartChar = 0
            var bestEndChar = 0

            while (bestEndLine >= currentLine) {
                val startChar = layoutResult.getLineStart(currentLine).coerceIn(0, docAnnotated.length)
                val endChar = layoutResult.getLineEnd(bestEndLine).coerceIn(startChar, docAnnotated.length)

                if (startChar >= endChar) {
                    bestEndLine--
                    continue
                }

                val slice = docAnnotated.subSequence(startChar, endChar)
                val candidateText = slice.trimTrailingWhitespace()

                if (candidateText.isEmpty()) {
                    bestEndLine--
                    continue
                }

                val candidateLayout = textMeasurer.measure(
                    text = candidateText,
                    style = textStyle,
                    constraints = Constraints(maxWidth = config.maxWidthPx)
                )
                val candidateHeight = candidateLayout.size.height.toFloat()

                if (candidateHeight <= safeMaxHeightPx || bestEndLine == currentLine) {
                    bestPageAnnotated = candidateText
                    bestStartChar = startChar
                    bestEndChar = startChar + candidateText.length
                    break
                }
                bestEndLine--
            }

            if (bestPageAnnotated != null && bestPageAnnotated.isNotEmpty()) {
                val startMainOffset = mapAnnotatedIndexToGlobalOffset(bestStartChar, offsetMapping, document.rawMainText.length)
                val endMainOffset = mapAnnotatedIndexToGlobalOffset(bestEndChar, offsetMapping, document.rawMainText.length)

                val page = ReaderPage(
                    pageIndex = pageIndex++,
                    text = bestPageAnnotated,
                    startOffset = startMainOffset,
                    endOffset = endMainOffset
                )

                val finalLayout = textMeasurer.measure(
                    text = page.text,
                    style = textStyle,
                    constraints = Constraints(maxWidth = config.maxWidthPx)
                )
                val actualHeight = finalLayout.size.height.toFloat()
                if (actualHeight > safeMaxHeightPx.toFloat()) {
                    Log.e("ReaderLayoutEngine", "PAGE OVERFLOW ERROR! Page ${page.pageIndex}, height $actualHeight > max $safeMaxHeightPx")
                } else {
                    Log.d("ReaderLayoutEngine", "Page OK: index ${page.pageIndex}, height $actualHeight / $safeMaxHeightPx")
                }

                accumulatedPages.add(page)

                if (isFirstPage) {
                    onPagesUpdated(accumulatedPages.toList(), true)
                    isFirstPage = false
                    yield()
                } else if (accumulatedPages.size % 10 == 0) {
                    onPagesUpdated(accumulatedPages.toList(), false)
                    yield()
                }

                currentLine = bestEndLine + 1
            } else {
                Log.e("ReaderLayoutEngine", "Line $currentLine exceeds max height or empty. Forcing advance.")
                currentLine++
            }
        }

        if (accumulatedPages.isNotEmpty()) {
            onPagesUpdated(accumulatedPages.toList(), false)
        }
        return@withContext accumulatedPages.toList()
    }

    private fun AnnotatedString.trimTrailingWhitespace(): AnnotatedString {
        var end = length
        while (end > 0 && text[end - 1].isWhitespace()) {
            end--
        }
        return if (end == length) this else subSequence(0, end)
    }
}
