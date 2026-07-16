package com.nightread.app.ui.customlayout

import android.content.Context
import android.graphics.Canvas
import android.text.StaticLayout
import android.util.AttributeSet
import android.view.View

/**
 * Кастомный View для отображения одной страницы текста.
 */
class CustomReaderPageView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private var layout: StaticLayout? = null
    private var isJustified: Boolean = false
    private var justifiedLines: List<JustifiedLine>? = null

    class JustifiedLine(
        val singleLineLayout: StaticLayout,
        val lineTop: Float
    )

    /**
     * Устанавливает макет страницы и перерисовывает View.
     */
    fun setLayout(layout: StaticLayout?, isJustified: Boolean = false) {
        this.layout = layout
        this.isJustified = isJustified
        this.justifiedLines = null
        
        if (layout != null && isJustified) {
            precomputeJustifiedLines(layout)
        }
        
        invalidate()
    }

    private fun precomputeJustifiedLines(layout: StaticLayout) {
        val list = ArrayList<JustifiedLine>()
        val lineCount = layout.lineCount
        val paint = layout.paint
        val sourceText = layout.text
        val availableWidth = layout.width
        
        val defaultLetterSpacing = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            paint.letterSpacing
        } else {
            0f
        }
        
        for (i in 0 until lineCount) {
            val start = layout.getLineStart(i)
            val end = layout.getLineEnd(i)
            val lineTop = layout.getLineTop(i).toFloat()
            
            val lineText = sourceText.subSequence(start, end)
            
            val isLastLineOfParagraph = i == lineCount - 1 || 
                    (end > start && sourceText[end - 1] == '\n')
            
            val letterSpacingToApply: Float
            if (isLastLineOfParagraph) {
                letterSpacingToApply = defaultLetterSpacing
            } else {
                val lineWidth = layout.getLineWidth(i)
                val extraSpace = availableWidth - lineWidth
                
                if (extraSpace > 0) {
                    val numChars = end - start
                    var visibleChars = numChars
                    while (visibleChars > 0 && sourceText[start + visibleChars - 1].isWhitespace()) {
                        visibleChars--
                    }
                    
                    if (visibleChars > 1) {
                        val textSize = paint.textSize
                        val sExtra = extraSpace / (textSize * (visibleChars - 1))
                        
                        // Ограничиваем максимальную разрядку до 0.12f, чтобы буквы не разъезжались слишком сильно
                        val appliedSExtra = sExtra.coerceIn(0f, 0.12f)
                        
                        letterSpacingToApply = defaultLetterSpacing + appliedSExtra
                    } else {
                        letterSpacingToApply = defaultLetterSpacing
                    }
                } else {
                    letterSpacingToApply = defaultLetterSpacing
                }
            }
            
            val tempPaint = android.text.TextPaint(paint)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                tempPaint.letterSpacing = letterSpacingToApply
            }
            
            val safeWidth = (availableWidth + 500).coerceAtLeast(1)
            val builder = StaticLayout.Builder.obtain(lineText, 0, lineText.length, tempPaint, safeWidth)
                .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1f)
                .setIncludePad(false)
                
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                builder.setBreakStrategy(android.graphics.text.LineBreaker.BREAK_STRATEGY_SIMPLE)
                builder.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE)
            }
            
            list.add(JustifiedLine(builder.build(), lineTop))
        }
        
        this.justifiedLines = list
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val currentLayout = layout ?: return
        
        canvas.save()
        canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())
        
        val lines = justifiedLines
        if (isJustified && lines != null) {
            for (line in lines) {
                canvas.save()
                canvas.translate(0f, line.lineTop)
                line.singleLineLayout.draw(canvas)
                canvas.restore()
            }
        } else {
            currentLayout.draw(canvas)
        }
        
        canvas.restore()
    }
}
