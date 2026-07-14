package com.nightread.app.ui

import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.os.Build

object PaginationManager {
    fun paginate(
        text: CharSequence,
        width: Int,
        height: Int,
        paint: TextPaint,
        lineSpacingMultiplier: Float,
        lineSpacingExtra: Float
    ): List<Pair<Int, Int>> {
        val pages = mutableListOf<Pair<Int, Int>>()
        var start = 0
        val textLength = text.length
        
        while (start < textLength) {
            val builder = StaticLayout.Builder.obtain(text, start, textLength, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(lineSpacingExtra, lineSpacingMultiplier)
                .setIncludePad(false)
                
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                builder.setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
                builder.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
            }
            
            val layout = builder.build()
            
            // Find how many lines fit
            var lineCount = 0
            var currentHeight = 0
            while (lineCount < layout.lineCount && currentHeight + layout.getLineBottom(lineCount) <= height) {
                currentHeight = layout.getLineBottom(lineCount)
                lineCount++
            }
            
            if (lineCount == 0) { // Text doesn't fit at all, advance by at least one char to avoid infinite loop
                start++
            } else {
                val end = layout.getLineEnd(lineCount - 1)
                pages.add(start to end)
                start = end
            }
        }
        return pages
    }
    
    fun createLayoutForPage(
        text: CharSequence,
        range: Pair<Int, Int>,
        width: Int,
        paint: TextPaint,
        lineSpacingMultiplier: Float,
        lineSpacingExtra: Float
    ): Layout {
        val builder = StaticLayout.Builder.obtain(text, range.first, range.second, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(lineSpacingExtra, lineSpacingMultiplier)
            .setIncludePad(false)
            
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            builder.setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
            builder.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
        }
        
        return builder.build()
    }
}
