package com.nightread.app.ui.customlayout

import android.text.StaticLayout
import android.text.TextPaint
import android.text.Layout
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.yield
import android.os.Build

object LayoutCache {
    private val layoutsCache = LruCache<String, LruCache<Int, StaticLayout>>(3)
    private val offsetsCache = LruCache<String, List<Int>>(3)
    
    fun getPage(configKey: String, offset: Int): StaticLayout? {
        return layoutsCache.get(configKey)?.get(offset)
    }
    
    fun putPage(configKey: String, offset: Int, layout: StaticLayout) {
        var pageMap = layoutsCache.get(configKey)
        if (pageMap == null) {
            pageMap = LruCache(50)
            layoutsCache.put(configKey, pageMap)
        }
        pageMap.put(offset, layout)
    }
    
    fun getOffsets(configKey: String): List<Int>? = offsetsCache.get(configKey)
    
    fun putOffsets(configKey: String, offsets: List<Int>) {
        offsetsCache.put(configKey, offsets)
    }
    
    fun clear() {
        layoutsCache.evictAll()
        offsetsCache.evictAll()
    }
}

class TextLayoutBuilder {
    private var text: CharSequence = ""
    private var width: Int = 0
    private var height: Int = 0
    private var paint: TextPaint = TextPaint()
    private var lineSpacingMultiplier: Float = 1.0f
    private var lineSpacingExtra: Float = 0f
    private var letterSpacing: Float = 0f
    private var alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
    private var hyphenation: Boolean = true
    
    fun setLetterSpacing(spacing: Float) = apply { this.letterSpacing = spacing }

    fun setText(text: CharSequence) = apply { this.text = text }
    fun setWidth(width: Int) = apply { this.width = width }
    fun setHeight(height: Int) = apply { this.height = height }
    fun setPaint(paint: TextPaint) = apply { this.paint = paint }
    fun setAlignment(alignment: Layout.Alignment) = apply { this.alignment = alignment }
    fun setLineSpacing(extra: Float, multiplier: Float) = apply {
        this.lineSpacingExtra = extra
        this.lineSpacingMultiplier = multiplier
    }
    fun setHyphenation(enabled: Boolean) = apply { this.hyphenation = enabled }
    
    val configKey: String
        get() = "${text.length}_${width}_${height}_${paint.textSize}_${paint.typeface?.hashCode()}_${lineSpacingMultiplier}_${alignment.name}_${hyphenation}_$letterSpacing"

    private fun createStaticLayout(source: CharSequence, start: Int, end: Int): StaticLayout {
        paint.letterSpacing = letterSpacing
        val strategy = if (hyphenation) android.graphics.text.LineBreaker.BREAK_STRATEGY_HIGH_QUALITY else android.graphics.text.LineBreaker.BREAK_STRATEGY_SIMPLE
        val frequency = if (hyphenation) Layout.HYPHENATION_FREQUENCY_FULL else Layout.HYPHENATION_FREQUENCY_NONE

        val builder = StaticLayout.Builder.obtain(source, start, end, paint, width)
            .setAlignment(alignment)
            .setLineSpacing(lineSpacingExtra, lineSpacingMultiplier)
            .setIncludePad(false)
            
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            builder.setBreakStrategy(strategy)
            builder.setHyphenationFrequency(frequency)
        }
            
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Enable inter-word justification for high-quality text layout
            builder.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD)
        }
            
        return builder.build()
    }

    suspend fun buildPagination(onProgress: ((List<Int>, Boolean) -> Unit)? = null): List<Int> = withContext(Dispatchers.Default) {
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

        paint.textLocale = java.util.Locale("ru", "RU")

        val offsets = mutableListOf<Int>()
        offsets.add(0)
        
        var currentOffset = 0
        var pagesFound = 0
        
        val textLength = text.length
        var currentChunkSize = 50000 

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
            
            val layout = createStaticLayout(text, currentOffset, measureEnd)
            
            var lineIdx = 0
            val lineCount = layout.lineCount
            var lastPageEndOffset = currentOffset
            
            while (lineIdx < lineCount) {
                if (!isActive) break
                
                var pageEndLine = lineIdx
                val startY = layout.getLineTop(lineIdx)
                var pageFull = false
                
                while (pageEndLine < lineCount) {
                    if (layout.getLineBottom(pageEndLine) - startY > height) {
                        pageFull = true
                        break
                    }
                    
                    val lineStartOffset = layout.getLineStart(pageEndLine)
                    val lineEndOffset = layout.getLineEnd(pageEndLine)
                    var hasPageBreak = false
                    for (i in lineStartOffset until lineEndOffset) {
                        if (text[i] == '\u000C') {
                            hasPageBreak = true
                            break
                        }
                    }
                    
                    if (hasPageBreak) {
                        pageFull = true
                        pageEndLine++
                        break
                    }
                    
                    pageEndLine++
                }
                
                if (pageEndLine == lineIdx) {
                    pageEndLine = lineIdx + 1
                    pageFull = true
                }
                
                val nextOffset = layout.getLineEnd(pageEndLine - 1)
                
                if (pageFull || measureEnd == textLength) {
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

    fun buildPageLayout(offset: Int, endOffset: Int): StaticLayout {
        // Build initial layout
        paint.textLocale = java.util.Locale("ru", "RU")
        var layout = createStaticLayout(text, offset, endOffset)
        
        // Pull-in algorithm
        if (layout.lineCount > 0) {
            val lastLineIdx = layout.lineCount - 1
            val lastLineLineWidth = layout.getLineRight(lastLineIdx) - layout.getLineLeft(lastLineIdx)
            // If last line is less than 15% of width
            if (lastLineLineWidth < width * 0.15f) {
                // Try to rebuild with slightly reduced letter spacing
                val originalLetterSpacing = letterSpacing
                letterSpacing -= 0.01f
                layout = createStaticLayout(text, offset, endOffset)
                letterSpacing = originalLetterSpacing
            }
        }
        
        return layout
    }
}
