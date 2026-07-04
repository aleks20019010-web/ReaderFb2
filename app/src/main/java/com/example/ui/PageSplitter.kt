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
     * Uses exact StaticLayout line bottom measurements to guarantee text fits the vertical height.
     */
    fun splitText(
        text: String,
        availableWidth: Int,
        availableHeight: Int,
        paint: TextPaint,
        lineSpacing: Float,
        alignment: String = "justify"
    ): PageResult {
        Log.d(TAG, "splitText called: availableWidth=$availableWidth, availableHeight=$availableHeight, lineSpacing=$lineSpacing")
        
        val pages = mutableListOf<String>()
        val offsets = mutableListOf<Int>()
        
        if (text.isEmpty() || availableWidth <= 0 || availableHeight <= 0) {
            return PageResult(listOf("Документ пуст."), listOf(0))
        }

        var start = 0
        val textLength = text.length

        val alignmentVal = when (alignment) {
            "left" -> Layout.Alignment.ALIGN_NORMAL
            "right" -> Layout.Alignment.ALIGN_OPPOSITE
            "center" -> Layout.Alignment.ALIGN_CENTER
            else -> Layout.Alignment.ALIGN_NORMAL
        }

        while (start < textLength) {
            offsets.add(start)

            // 1. Determine a segment of text to measure
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
                
                // Check if all lines fit within the available height
                var fitsAll = true
                for (i in 0 until tempLayout.lineCount) {
                    if (tempLayout.getLineBottom(i) > availableHeight) {
                        fitsAll = false
                        break
                    }
                }
                
                // If the entire chunk fits and there is more text, double the chunk size to scan further
                if (fitsAll && measureEnd < textLength) {
                    chunkSize *= 2
                } else {
                    break
                }
            }

            // 2. Find the exact number of lines that fit in availableHeight
            var fitLineCount = 0
            for (i in 0 until tempLayout.lineCount) {
                if (tempLayout.getLineBottom(i) <= availableHeight) {
                    fitLineCount = i + 1
                } else {
                    break
                }
            }
            
            // Safety fallback: fit at least 1 line
            if (fitLineCount == 0) {
                fitLineCount = 1
            }

            var end = tempLayout.getLineEnd(fitLineCount - 1)
            
            // Log precise layout metrics to verify correctness as per requirement #5
            val lastLineBottom = tempLayout.getLineBottom(fitLineCount - 1)
            val diff = availableHeight - lastLineBottom
            Log.d(TAG, "Page Split: start=$start, end=$end, fitLineCount=$fitLineCount, " +
                    "availableHeight=$availableHeight, actualPageHeight=$lastLineBottom, diff=$diff")

            if (end <= start) {
                end = (start + 1).coerceAtMost(textLength)
            }

            // 3. Scan for manual page/chapter breaks (\u000C) in the current page text block
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
                // Apply word-boundary refinement to avoid breaking in the middle of words
                if (end < textLength) {
                    var spaceIndex = -1
                    // Search backward for whitespace to find a suitable word boundary
                    for (j in (end - 1) downTo (end - 100).coerceAtLeast(start)) {
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
