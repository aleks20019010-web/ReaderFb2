package com.nightread.app.ui.customlayout

import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.coroutines.coroutineContext

object PageSplitter {
    private val linesCache = mutableMapOf<String, Int>()

    fun getLinesPerPage(height: Int, paint: TextPaint, lineSpacingMultiplier: Float, lineSpacingExtra: Float): Int {
        val key = "${height}_${paint.textSize}_${lineSpacingMultiplier}_${lineSpacingExtra}"
        
        return linesCache.getOrPut(key) {
            val fm = paint.fontMetrics
            val lineHeight = (fm.descent - fm.ascent) * lineSpacingMultiplier + lineSpacingExtra
            val lines = (height / lineHeight).toInt()
            val finalLines = maxOf(3, lines)
            Log.d("PageSplitter", "Calculated linesPerPage: $finalLines for height: $height, lineHeight: $lineHeight")
            finalLines
        }
    }

    private fun findPageEnd(
        text: CharSequence,
        layout: StaticLayout,
        currentOffset: Int,
        lineIdx: Int,
        linesPerPage: Int,
        measureEnd: Int,
        width: Int
    ): Pair<Int, Int> { // Returns Pair<NextOffset, PageEndLine>
        val lineCount = layout.lineCount
        var pageEndLine = minOf(lineIdx + linesPerPage, lineCount)
        
        // Check for hard page breaks
        var hasPageBreak = false
        for (i in lineIdx until pageEndLine) {
            val lineStart = layout.getLineStart(i)
            val lineEnd = layout.getLineEnd(i)
            var foundBreak = false
            for (j in lineStart until lineEnd) {
                if (text[j] == '\u000C') {
                    pageEndLine = i + 1
                    foundBreak = true
                    hasPageBreak = true
                    break
                }
            }
            if (foundBreak) break
        }
        
        val originalNextOffset = layout.getLineEnd(pageEndLine - 1)
        var nextOffset = originalNextOffset

        if (pageEndLine < lineCount || measureEnd == text.length) {
            if (nextOffset > currentOffset && nextOffset < text.length && !hasPageBreak) {
                // Protect from breaking middle of word
                val before = text[nextOffset - 1]
                val after = text[nextOffset]
                val isBoundaryBefore = before.isWhitespace() || before == '-' || before == '\n'
                val isBoundaryAfter = after.isWhitespace() || after == '\n'

                if (!isBoundaryBefore && !isBoundaryAfter) {
                    var backtrack = nextOffset - 1
                    while (backtrack > currentOffset) {
                        val c = text[backtrack]
                        if (c.isWhitespace() || c == '-' || c == '\n') {
                            backtrack++
                            break
                        }
                        backtrack--
                    }
                    if (backtrack > currentOffset) {
                        nextOffset = backtrack
                        Log.d("PageSplitter", "Word break protected, nextOffset: $nextOffset")
                    }
                } else {
                    // Find length of last word
                    var charsCount = 0
                    var backtrack = nextOffset - 1
                    while (backtrack > currentOffset) {
                        val c = text[backtrack]
                        if (c.isWhitespace() || c == '\n' || c == '-') {
                            break
                        }
                        charsCount++
                        backtrack--
                    }
                    
                    // Orphan protection: 1-2 chars
                    if (charsCount in 1..2 && backtrack + 1 > currentOffset) {
                        Log.d("PageSplitter", "Orphan word ($charsCount chars) protected, nextOffset: ${backtrack + 1}")
                        nextOffset = backtrack + 1
                    } else {
                        // 30% width protection
                        val lastLine = pageEndLine - 1
                        val lineWidth = layout.getLineWidth(lastLine)
                        if (lineWidth < width * 0.3f && charsCount > 2 && backtrack + 1 > currentOffset) {
                            Log.d("PageSplitter", "Last line < 30% width, moving last word to next page, nextOffset: ${backtrack + 1}")
                            nextOffset = backtrack + 1
                        }
                    }
                }
            }
        }
        
        return Pair(nextOffset, pageEndLine)
    }

    suspend fun buildPagination(
        text: CharSequence,
        width: Int,
        height: Int,
        paint: TextPaint,
        lineSpacingMultiplier: Float,
        lineSpacingExtra: Float,
        hyphenation: Boolean,
        alignment: android.text.Layout.Alignment,
        letterSpacing: Float,
        configKey: String,
        onProgress: ((List<Int>, Boolean) -> Unit)? = null
    ): List<Int> = withContext(Dispatchers.Default) {
        if (text.isEmpty() || width <= 0 || height <= 0) {
            val res = listOf(0)
            withContext(Dispatchers.Main) { onProgress?.invoke(res, true) }
            return@withContext res
        }

        val cached = LayoutCache.getOffsets(configKey)
        if (cached != null) {
            withContext(Dispatchers.Main) { onProgress?.invoke(cached, true) }
            return@withContext cached
        }

        val offsets = mutableListOf<Int>()
        offsets.add(0)

        var currentOffset = 0
        var pagesFound = 0
        val textLength = text.length
        
        val linesPerPage = getLinesPerPage(height, paint, lineSpacingMultiplier, lineSpacingExtra)
        
        var currentChunkSize = 50000 
        
        paint.letterSpacing = letterSpacing
        paint.textLocale = java.util.Locale("ru", "RU")

        while (currentOffset < textLength) {
            if (!isActive) break
            
            var measureEnd = minOf(currentOffset + currentChunkSize, textLength)
            if (measureEnd < textLength) {
                var lastNewLine = measureEnd
                while (lastNewLine > currentOffset && text[lastNewLine] != '\n' && text[lastNewLine] != '\u000C') {
                    lastNewLine--
                }
                if (lastNewLine > currentOffset) {
                    measureEnd = lastNewLine + 1
                }
            }

            val layout = createStaticLayout(text, currentOffset, measureEnd, paint, width, alignment, lineSpacingMultiplier, lineSpacingExtra, hyphenation)
            var lineIdx = 0
            val lineCount = layout.lineCount
            var lastPageEndOffset = currentOffset

            while (lineIdx < lineCount) {
                if (!isActive) break

                var pageEndLine = minOf(lineIdx + linesPerPage, lineCount)
                
                // Check for hard page breaks
                var hasPageBreak = false
                for (i in lineIdx until pageEndLine) {
                    val lineStart = layout.getLineStart(i)
                    val lineEnd = layout.getLineEnd(i)
                    var foundBreak = false
                    for (j in lineStart until lineEnd) {
                        if (text[j] == '\u000C') {
                            pageEndLine = i + 1
                            foundBreak = true
                            hasPageBreak = true
                            break
                        }
                    }
                    if (foundBreak) break
                }
                
                val originalNextOffset = layout.getLineEnd(pageEndLine - 1)
                var nextOffset = originalNextOffset

                if (pageEndLine < lineCount || measureEnd == textLength) {
                    if (nextOffset > currentOffset && nextOffset < textLength && !hasPageBreak) {
                        // Protect from breaking middle of word
                        val before = text[nextOffset - 1]
                        val after = text[nextOffset]
                        val isBoundaryBefore = before.isWhitespace() || before == '-' || before == '\n'
                        val isBoundaryAfter = after.isWhitespace() || after == '\n'

                        if (!isBoundaryBefore && !isBoundaryAfter) {
                            var backtrack = nextOffset - 1
                            while (backtrack > currentOffset) {
                                val c = text[backtrack]
                                if (c.isWhitespace() || c == '-' || c == '\n') {
                                    backtrack++
                                    break
                                }
                                backtrack--
                            }
                            if (backtrack > currentOffset) {
                                nextOffset = backtrack
                                Log.d("PageSplitter", "Word break protected, nextOffset: $nextOffset")
                            }
                        } else {
                            // Find length of last word
                            var charsCount = 0
                            var backtrack = nextOffset - 1
                            while (backtrack > currentOffset) {
                                val c = text[backtrack]
                                if (c.isWhitespace() || c == '\n' || c == '-') {
                                    break
                                }
                                charsCount++
                                backtrack--
                            }
                            
                            // Orphan protection: 1-2 chars
                            if (charsCount in 1..2 && backtrack + 1 > currentOffset) {
                                Log.d("PageSplitter", "Orphan word ($charsCount chars) protected, nextOffset: ${backtrack + 1}")
                                nextOffset = backtrack + 1
                            } else {
                                // 30% width protection
                                val lastLine = pageEndLine - 1
                                val lineWidth = layout.getLineWidth(lastLine)
                                if (lineWidth < width * 0.3f && charsCount > 2 && backtrack + 1 > currentOffset) {
                                    Log.d("PageSplitter", "Last line < 30% width, moving last word to next page, nextOffset: ${backtrack + 1}")
                                    nextOffset = backtrack + 1
                                }
                            }
                        }
                    }

                    if (nextOffset < textLength) {
                        offsets.add(nextOffset)
                        pagesFound++
                        if (pagesFound % 20 == 0) {
                            val offsetsCopy = ArrayList(offsets)
                            withContext(Dispatchers.Main) {
                                onProgress?.invoke(offsetsCopy, false)
                            }
                        }
                    }
                    lastPageEndOffset = nextOffset
                    lineIdx = pageEndLine

                    if (nextOffset != originalNextOffset) {
                        break
                    }
                } else {
                    break
                }
            }

            if (lastPageEndOffset == currentOffset) {
                if (measureEnd == textLength) break
                currentChunkSize *= 2
            } else {
                currentOffset = lastPageEndOffset
            }
            yield()
        }

        if (isActive) {
            LayoutCache.putOffsets(configKey, offsets)
            withContext(Dispatchers.Main) {
                onProgress?.invoke(offsets, true)
            }
        }
        return@withContext offsets
    }

    private fun createStaticLayout(
        source: CharSequence, start: Int, end: Int, 
        paint: TextPaint, width: Int, 
        alignment: android.text.Layout.Alignment,
        lineSpacingMultiplier: Float, lineSpacingExtra: Float,
        hyphenation: Boolean
    ): StaticLayout {
        val strategy = if (hyphenation) android.graphics.text.LineBreaker.BREAK_STRATEGY_HIGH_QUALITY else android.graphics.text.LineBreaker.BREAK_STRATEGY_SIMPLE
        val frequency = if (hyphenation) android.text.Layout.HYPHENATION_FREQUENCY_FULL else android.text.Layout.HYPHENATION_FREQUENCY_NONE
        
        val safeWidth = (width - 4).coerceAtLeast(1)
        val builder = StaticLayout.Builder.obtain(source, start, end, paint, safeWidth)
            .setAlignment(alignment)
            .setLineSpacing(lineSpacingExtra, lineSpacingMultiplier)
            .setIncludePad(true)
            
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            builder.setBreakStrategy(strategy)
            builder.setHyphenationFrequency(frequency)
        }
        
        return builder.build()
    }
}
