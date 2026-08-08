package com.nightread.app.ui.customlayout

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

data class ReaderPage(
    val text: AnnotatedString,
    val startOffset: Int,
    val endOffset: Int
)

private data class OpenTagInfo(val tagName: String, val startIndex: Int)

object PaginationEngine {

    suspend fun paginate(
        mainText: String,
        fontSize: androidx.compose.ui.unit.TextUnit,
        font: FontFamily,
        fontWeight: FontWeight,
        lineSpacing: Float,
        textMeasurer: TextMeasurer,
        maxWidthPx: Int,
        maxHeightPx: Int,
        density: Density,
        onPagesUpdated: (List<ReaderPage>, Boolean) -> Unit
    ): List<ReaderPage> = withContext(Dispatchers.Default) {
        if (mainText.isBlank() || maxWidthPx <= 0 || maxHeightPx <= 0) return@withContext emptyList()

        val formattedText = com.nightread.app.utils.TypographyUtils.applyMicroTypography(mainText)
        val textStyle = TextStyle(
            fontSize = fontSize,
            fontFamily = font,
            fontWeight = fontWeight,
            textAlign = TextAlign.Justify,
            lineHeight = (fontSize.value * lineSpacing).sp,
            letterSpacing = 0.1.sp,
            lineBreak = LineBreak.Paragraph,
            hyphens = Hyphens.Auto,
            platformStyle = PlatformTextStyle(includeFontPadding = false)
        )

        val safeMaxHeightPx = (maxHeightPx - with(density) { 10.dp.toPx() }).toInt().coerceAtLeast(1)

        val rawSections = formattedText.split('\u000C')
        val chapterSections = mutableListOf<String>()

        for (sec in rawSections) {
            val trimmed = sec.trim()
            if (trimmed.isEmpty()) continue
            if (trimmed.length > 100000) {
                val paragraphs = trimmed.split('\n')
                val sb = StringBuilder()
                for (p in paragraphs) {
                    if (sb.length + p.length > 80000 && sb.isNotEmpty()) {
                        chapterSections.add(sb.toString().trimEnd())
                        sb.clear()
                    }
                    if (sb.isNotEmpty()) sb.append("\n")
                    sb.append(p)
                }
                if (sb.isNotEmpty()) {
                    chapterSections.add(sb.toString().trimEnd())
                }
            } else {
                chapterSections.add(trimmed)
            }
        }

        if (chapterSections.isEmpty()) return@withContext emptyList()

        val accumulatedPages = mutableListOf<ReaderPage>()
        var runningMainTextOffset = 0
        var isFirstPage = true

        for (cleanSection in chapterSections) {
            val sectionLength = cleanSection.length
            val sectionAnnotated = parseFormattedTextToAnnotatedString(cleanSection, fontSize)
            if (sectionAnnotated.isEmpty()) {
                runningMainTextOffset += sectionLength + 1
                continue
            }

            val layoutResult = textMeasurer.measure(
                text = sectionAnnotated,
                style = textStyle,
                constraints = Constraints(maxWidth = maxWidthPx)
            )

            val lineCount = layoutResult.lineCount
            if (lineCount == 0) {
                runningMainTextOffset += sectionLength + 1
                continue
            }

            var currentLine = 0
            while (currentLine < lineCount) {
                val pageTop = layoutResult.getLineTop(currentLine)
                var candidateEndLine = currentLine
                while (candidateEndLine + 1 < lineCount &&
                    (layoutResult.getLineBottom(candidateEndLine + 1) - pageTop) <= safeMaxHeightPx
                ) {
                    candidateEndLine++
                }

                var validEndLine = candidateEndLine
                var finalPageAnnotated: AnnotatedString? = null
                var finalStartChar = 0
                var finalEndChar = 0

                while (validEndLine >= currentLine) {
                    val startChar = layoutResult.getLineStart(currentLine).coerceIn(0, sectionAnnotated.length)
                    finalStartChar = startChar
                    val endChar = layoutResult.getLineEnd(validEndLine).coerceIn(startChar, sectionAnnotated.length)
                    finalEndChar = endChar

                    val pageSlice = sectionAnnotated.subSequence(startChar, endChar)
                    val candidatePage = pageSlice.trimTrailingWhitespace()

                    if (candidatePage.isEmpty()) {
                        validEndLine--
                        continue
                    }

                    val pageBottom = layoutResult.getLineBottom(validEndLine)
                    val pageHeight = pageBottom - pageTop

                    if (pageHeight <= safeMaxHeightPx || validEndLine == currentLine) {
                        finalPageAnnotated = candidatePage
                        break
                    }
                    validEndLine--
                }

                if (finalPageAnnotated != null && finalPageAnnotated.isNotEmpty()) {
                    val pageMainTextOffset = runningMainTextOffset + ((finalStartChar.toDouble() / sectionAnnotated.length.coerceAtLeast(1)) * sectionLength).toInt()
                    val pageEndMainOffset = runningMainTextOffset + ((finalEndChar.toDouble() / sectionAnnotated.length.coerceAtLeast(1)) * sectionLength).toInt()
                    
                    val readerPage = ReaderPage(
                        text = finalPageAnnotated,
                        startOffset = pageMainTextOffset.coerceIn(0, mainText.length),
                        endOffset = pageEndMainOffset.coerceIn(0, mainText.length)
                    )
                    accumulatedPages.add(readerPage)

                    if (isFirstPage) {
                        onPagesUpdated(accumulatedPages.toList(), true)
                        isFirstPage = false
                        yield()
                    } else if (accumulatedPages.size % 10 == 0) {
                        onPagesUpdated(accumulatedPages.toList(), false)
                        yield()
                    }
                    val nextLine = validEndLine + 1
                    if (nextLine > currentLine) {
                        currentLine = nextLine
                    } else {
                        currentLine++
                    }
                } else {
                    currentLine++
                }
            }
            runningMainTextOffset += sectionLength + 1
        }

        if (accumulatedPages.isNotEmpty()) {
            onPagesUpdated(accumulatedPages.toList(), false)
        }
        return@withContext accumulatedPages.toList()
    }

    private fun parseFormattedTextToAnnotatedString(text: String, baseFontSize: androidx.compose.ui.unit.TextUnit): AnnotatedString {
        return androidx.compose.ui.text.buildAnnotatedString {
            val regex = Regex("<(/?)(b|i|em|s|strike|del|sup|sub|code|title|h1|h2)>", RegexOption.IGNORE_CASE)
            val matches = regex.findAll(text)
            var currentIndex = 0
            val openTags = mutableListOf<OpenTagInfo>()

            for (match in matches) {
                val matchRange = match.range
                if (matchRange.first > currentIndex) {
                    append(text.substring(currentIndex, matchRange.first))
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
                    openTags.add(OpenTagInfo(tagName, length))
                } else {
                    val openTagIndex = openTags.indexOfLast { it.tagName == tagName }
                    if (openTagIndex != -1) {
                        val openTag = openTags.removeAt(openTagIndex)
                        val start = openTag.startIndex
                        val end = length
                        if (end > start) {
                            applyTagStyle(tagName, start, end, baseFontSize)
                        }
                    }
                }
                currentIndex = matchRange.last + 1
            }

            if (currentIndex < text.length) {
                append(text.substring(currentIndex))
            }

            for (openTag in openTags) {
                val start = openTag.startIndex
                val end = length
                if (end > start) {
                    applyTagStyle(openTag.tagName, start, end, baseFontSize)
                }
            }
        }
    }

    private fun androidx.compose.ui.text.AnnotatedString.Builder.applyTagStyle(
        tagName: String,
        start: Int,
        end: Int,
        baseFontSize: androidx.compose.ui.unit.TextUnit
    ) {
        val style = when (tagName) {
            "strong" -> androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)
            "emphasis" -> androidx.compose.ui.text.SpanStyle(fontStyle = FontStyle.Italic)
            "strikethrough" -> androidx.compose.ui.text.SpanStyle(textDecoration = TextDecoration.LineThrough)
            "sup" -> androidx.compose.ui.text.SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = baseFontSize * 0.75f)
            "sub" -> androidx.compose.ui.text.SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = baseFontSize * 0.75f)
            "code" -> androidx.compose.ui.text.SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x22888888))
            "chapter" -> androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, fontSize = baseFontSize * 1.5f)
            else -> null
        }
        if (style != null) {
            addStyle(style, start, end)
        }
    }

    private fun AnnotatedString.trimTrailingWhitespace(): AnnotatedString {
        var end = length
        while (end > 0 && text[end - 1].isWhitespace()) {
            end--
        }
        return if (end == length) this else subSequence(0, end)
    }
}
