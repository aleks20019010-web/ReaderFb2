package com.example.ui

import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log

object PageSplitter {
    private const val TAG = "PageSplitter"

    data class PageResult(
        val pages: List<String>,
        val offsets: List<Int>
    )

    /**
     * Splits the book content into pages of text that fit within the given height and width.
     * Honors form-feed characters (\u000C) as page breaks for starting new chapters on new pages.
     */
    fun splitText(
        text: String,
        availableWidth: Int,
        availableHeight: Int,
        paint: TextPaint,
        lineSpacing: Float,
        alignment: String = "justify"
    ): PageResult {
        Log.d(TAG, "splitText called with width: $availableWidth, height: $availableHeight, lineSpacing: $lineSpacing")
        
        val pages = mutableListOf<String>()
        val offsets = mutableListOf<Int>()
        
        if (text.isEmpty() || availableWidth <= 0 || availableHeight <= 0) {
            return PageResult(listOf("Документ пуст."), listOf(0))
        }

        // Calculate max lines that fit vertically on the screen
        val fontHeight = paint.fontSpacing * lineSpacing
        val maxLines = (availableHeight / fontHeight).toInt().coerceAtLeast(1)
        Log.d(TAG, "Font height: $fontHeight, Max lines per page: $maxLines")

        var start = 0
        val textLength = text.length

        while (start < textLength) {
            offsets.add(start)

            // 1. Create a layout of the remaining text to measure line ends
            val alignmentVal = when (alignment) {
                "left" -> Layout.Alignment.ALIGN_NORMAL
                "right" -> Layout.Alignment.ALIGN_OPPOSITE
                "center" -> Layout.Alignment.ALIGN_CENTER
                else -> Layout.Alignment.ALIGN_NORMAL
            }

            var chunkSize = 8000
            var tempLayout: StaticLayout
            var measureEnd: Int
            while (true) {
                measureEnd = (start + chunkSize).coerceAtMost(textLength)
                tempLayout = StaticLayout.Builder.obtain(
                    text, start, measureEnd, paint, availableWidth
                )
                .setAlignment(alignmentVal)
                .setLineSpacing(0f, lineSpacing)
                .setIncludePad(false)
                .build()
                
                if (tempLayout.lineCount >= maxLines || measureEnd == textLength) {
                    break
                }
                chunkSize *= 2
            }

            val actualLines = tempLayout.lineCount.coerceAtMost(maxLines)
            var end = tempLayout.getLineEnd(actualLines - 1)

            if (end <= start) {
                end = (start + 1).coerceAtMost(textLength)
            }

            // 2. Scan for any manual page/chapter breaks (\u000C) in the current page text block
            var foundChapterBreakIdx = -1
            for (idx in start until end.coerceAtMost(textLength)) {
                if (text[idx] == '\u000C') {
                    foundChapterBreakIdx = idx
                    break
                }
            }

            if (foundChapterBreakIdx != -1) {
                // If a chapter break is found, end the page right before it
                end = foundChapterBreakIdx
                val pageText = text.substring(start, end)
                pages.add(pageText)
                // Skip the \u000C character on the next iteration
                start = foundChapterBreakIdx + 1
            } else {
                // No chapter break in this segment. Apply standard word-boundary refinement.
                if (end < textLength) {
                    var spaceIndex = -1
                    // Search backward for a whitespace to avoid cutting a word in half
                    for (j in (end - 1) downTo (end - 30).coerceAtLeast(start)) {
                        if (text[j].isWhitespace()) {
                            spaceIndex = j
                            break
                        }
                    }
                    if (spaceIndex > start) {
                        end = spaceIndex + 1
                    }
                }
                
                val pageText = text.substring(start, end)
                pages.add(pageText)
                start = end
            }
        }

        Log.d(TAG, "Completed pagination. Total pages: ${pages.size}")
        return PageResult(pages, offsets)
    }
}
