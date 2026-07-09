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
        var pages: MutableList<String> = mutableListOf(),
        var offsets: MutableList<Int> = mutableListOf(),
        var isFinished: Boolean = false
    )

    /**
     * Finds the exact page containing the target character offset.
     * This is useful for immediately jumping to a page before full pagination completes.
     */
    suspend fun getPageForOffset(
        text: String,
        targetOffset: Int,
        availableWidth: Int,
        availableHeight: Int,
        paint: TextPaint,
        lineSpacing: Float,
        alignment: String = "justify"
    ): Pair<Int, String> = withContext(Dispatchers.Default) {
        if (text.isEmpty() || availableWidth <= 0 || availableHeight <= 0) return@withContext Pair(0, "Документ пуст.")
        
        // Find paragraph start
        var start = targetOffset
        while (start > 0 && text[start - 1] != '\n' && text[start - 1] != '\u000C') {
            start--
        }
        
        val textLength = text.length
        val alignmentVal = when (alignment) {
            "left" -> Layout.Alignment.ALIGN_NORMAL
            "right" -> Layout.Alignment.ALIGN_OPPOSITE
            "center" -> Layout.Alignment.ALIGN_CENTER
            else -> Layout.Alignment.ALIGN_NORMAL
        }
        
        val tempLayout = StaticLayout.Builder.obtain(
            text, start, (start + 8000).coerceAtMost(textLength), paint, availableWidth
        )
            .setAlignment(alignmentVal)
            .setLineSpacing(0f, lineSpacing)
            .setIncludePad(false)
            .build()
            
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
        if (end <= start) end = (start + 1).coerceAtMost(textLength)
        
        // Note: For true lazy loading we need to just paginate incrementally from the beginning
        // But we will use the incremental progressive pagination to update the ViewPager.
        return@withContext Pair(start, text.substring(start, end))
    }

    suspend fun splitTextProgressive(
        text: String,
        availableWidth: Int,
        availableHeight: Int,
        paint: TextPaint,
        lineSpacing: Float,
        alignment: String = "justify",
        onProgress: (PageResult) -> Unit
    ) = withContext(Dispatchers.Default) {
        val result = PageResult()
        
        if (text.isEmpty() || availableWidth <= 0 || availableHeight <= 0) {
            result.pages.add("Документ пуст.")
            result.offsets.add(0)
            result.isFinished = true
            withContext(Dispatchers.Main) { onProgress(result) }
            return@withContext
        }

        var start = 0
        val textLength = text.length
        val alignmentVal = when (alignment) {
            "left" -> Layout.Alignment.ALIGN_NORMAL
            "right" -> Layout.Alignment.ALIGN_OPPOSITE
            "center" -> Layout.Alignment.ALIGN_CENTER
            else -> Layout.Alignment.ALIGN_NORMAL
        }

        var pagesFound = 0
        while (start < textLength) {
            if (!isActive) return@withContext
            result.offsets.add(start)

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
                result.pages.add(text.substring(start, end))
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
                result.pages.add(text.substring(start, end))
                start = end
            }

            pagesFound++
            // Report progress every 10 pages or on the first page
            if (pagesFound == 1 || pagesFound % 10 == 0) {
                withContext(Dispatchers.Main) {
                    // Send a copy so UI thread iterates safely
                    onProgress(PageResult(ArrayList(result.pages), ArrayList(result.offsets), false))
                }
            }
        }

        result.isFinished = true
        withContext(Dispatchers.Main) {
            onProgress(PageResult(ArrayList(result.pages), ArrayList(result.offsets), true))
        }
    }

    suspend fun splitText(
        text: String,
        availableWidth: Int,
        availableHeight: Int,
        paint: TextPaint,
        lineSpacing: Float,
        alignment: String = "justify"
    ): PageResult {
        var finalResult = PageResult()
        splitTextProgressive(text, availableWidth, availableHeight, paint, lineSpacing, alignment) {
            if (it.isFinished) {
                finalResult = it
            }
        }
        return finalResult
    }
}
