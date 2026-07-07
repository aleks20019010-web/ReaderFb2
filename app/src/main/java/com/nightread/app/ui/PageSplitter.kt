package com.nightread.app.ui

import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive

object PageSplitter {
    private const val TAG = "PageSplitter"

    data class PageResult(
        val pages: List<String>,
        val offsets: List<Int>
    )

    /**
     * Splits the book content into pages of text that fit within the given height and width.
     * Honors form-feed characters (\u000C) as page breaks.
     * Uses exact StaticLayout line bottom measurements to guarantee text fits the vertical height.
     */
    suspend fun splitText(
        text: String,
        availableWidth: Int,
        availableHeight: Int,
        paint: TextPaint,
        lineSpacing: Float,
        alignment: String = "justify"
    ): PageResult = withContext(Dispatchers.Default) {
        Log.d(TAG, "splitText called: availableWidth=$availableWidth, availableHeight=$availableHeight, lineSpacing=$lineSpacing")
        
        val pages = mutableListOf<String>()
        val offsets = mutableListOf<Int>()
        
        if (text.isEmpty() || availableWidth <= 0 || availableHeight <= 0) {
            return@withContext PageResult(listOf("Документ пуст."), listOf(0))
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
            if (!isActive) return@withContext PageResult(emptyList(), emptyList())
            offsets.add(start)

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
                
                var fitsAll = true
                for (i in 0 until tempLayout.lineCount) {
                    if (tempLayout.getLineBottom(i) > availableHeight) {
                        fitsAll = false
                        break
                    }
                }
                
                if (fitsAll && measureEnd < textLength) {
                    chunkSize *= 2
                } else {
                    break
                }
            }

            var fitLineCount = 0
            for (i in 0 until tempLayout.lineCount) {
                if (tempLayout.getLineBottom(i) <= availableHeight) {
                    fitLineCount = i + 1
                } else {
                    break
                }
            }
            
            if (fitLineCount == 0) fitLineCount = 1

            var end = tempLayout.getLineEnd(fitLineCount - 1)

            if (end <= start) {
                end = (start + 1).coerceAtMost(textLength)
            }

            var foundChapterBreakIdx = -1
            for (idx in start until end.coerceAtMost(textLength)) {
                if (text[idx] == '\u000C') {
                    foundChapterBreakIdx = idx
                    break
                }
            }

            if (foundChapterBreakIdx != -1) {
                end = foundChapterBreakIdx
                pages.add(text.substring(start, end))
                start = foundChapterBreakIdx + 1
            } else {
                if (end < textLength) {
                    var spaceIndex = -1
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
                
                pages.add(text.substring(start, end))
                start = end
            }
        }
        
        Log.d(TAG, "Completed pagination. Total pages: ${pages.size}")
        return@withContext PageResult(pages, offsets)
    }
}
