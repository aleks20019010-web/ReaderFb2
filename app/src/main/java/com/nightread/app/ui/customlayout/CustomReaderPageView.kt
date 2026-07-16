package com.nightread.app.ui.customlayout

import android.content.Context
import android.graphics.Canvas
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View

/**
 * Кастомный View для отображения одной страницы текста.
 */
class CustomReaderPageView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private var layout: StaticLayout? = null
    private var isJustified: Boolean = false
    private var justifiedLines: List<JustifiedLine>? = null

    sealed class JustifiedLine {
        class LetterSpacedLine(val singleLineLayout: StaticLayout, val lineTop: Float) : JustifiedLine()
        class WordSpacedLine(val words: List<String>, val gapWidth: Float, val lineTop: Float, val lineBaseline: Float, val paint: TextPaint) : JustifiedLine()
        class NormalLine(val singleLineLayout: StaticLayout, val lineTop: Float) : JustifiedLine()
    }

    /**
     * Устанавливает макет страницы и перерисовывает View.
     */
    fun setLayout(layout: StaticLayout?, isJustified: Boolean = false) {
        this.layout = layout
        this.isJustified = isJustified
        this.justifiedLines = null
        
        if (layout != null) {
            precomputeJustifiedLines(layout)
        }
        
        invalidate()
    }

    private fun ensureVisibleHyphen(text: CharSequence): CharSequence {
        val str = text.toString()
        var lastCharIdx = str.length - 1
        while (lastCharIdx >= 0 && (str[lastCharIdx].isWhitespace() || str[lastCharIdx] == '\n' || str[lastCharIdx] == '\r')) {
            lastCharIdx--
        }
        if (lastCharIdx >= 0 && str[lastCharIdx] == '\u00AD') {
            if (text is android.text.Spanned) {
                val ssb = android.text.SpannableStringBuilder(text)
                ssb.replace(lastCharIdx, lastCharIdx + 1, "-")
                return ssb
            } else {
                return str.substring(0, lastCharIdx) + "-" + str.substring(lastCharIdx + 1)
            }
        }
        return text
    }

    private fun precomputeJustifiedLines(layout: StaticLayout) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // На Android 10+ используем нативный высококачественный механизм выравнивания по ширине (StaticLayout).
            // Он работает идеально на уровне системы, не ломает переносы и полностью решает проблему наложений.
            this.justifiedLines = null
            return
        }

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
            val lineBaseline = (layout.getLineBaseline(i) - layout.getLineTop(i)).toFloat()
            
            val lineText = ensureVisibleHyphen(sourceText.subSequence(start, end))
            
            val lineAlignment = layout.getParagraphAlignment(i)
            val isNotNormalAlign = lineAlignment != android.text.Layout.Alignment.ALIGN_NORMAL
            
            val isLastLineOfParagraph = i == lineCount - 1 || 
                    (end > start && sourceText[end - 1] == '\n') || isNotNormalAlign
            
            if (!isJustified || isLastLineOfParagraph) {
                // Создаем отдельный StaticLayout для этой строки, чтобы нарисовать ее без выключки по ширине
                val tempPaint = TextPaint(paint)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    tempPaint.letterSpacing = defaultLetterSpacing
                }
                val layoutWidth = if (isNotNormalAlign) availableWidth else (availableWidth + 500).coerceAtLeast(1)
                val builder = StaticLayout.Builder.obtain(lineText, 0, lineText.length, tempPaint, layoutWidth)
                    .setAlignment(lineAlignment)
                    .setLineSpacing(0f, 1f)
                    .setIncludePad(false)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    builder.setBreakStrategy(android.graphics.text.LineBreaker.BREAK_STRATEGY_SIMPLE)
                    builder.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE)
                }
                list.add(JustifiedLine.NormalLine(builder.build(), lineTop))
            } else {
                if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P) {
                    // Android 9 и ниже — только растяжение пробелов без межбуквенной разрядки
                    // Триммим пробельные символы в конце строки
                    var textStr = lineText.toString()
                    while (textStr.isNotEmpty() && textStr.last().isWhitespace()) {
                        textStr = textStr.substring(0, textStr.length - 1)
                    }
                    val words = textStr.split(Regex("\\s+")).filter { it.isNotEmpty() }
                    
                    if (words.size > 1) {
                        val totalWordsWidth = words.sumOf { paint.measureText(it).toDouble() }.toFloat()
                        val extraSpace = availableWidth - totalWordsWidth
                        val gapWidth = extraSpace / (words.size - 1)
                        list.add(JustifiedLine.WordSpacedLine(words, gapWidth, lineTop, lineBaseline, TextPaint(paint)))
                    } else {
                        // Если в строке только одно слово, рисуем его нормально
                        val tempPaint = TextPaint(paint)
                        val builder = StaticLayout.Builder.obtain(lineText, 0, lineText.length, tempPaint, availableWidth + 200)
                            .setAlignment(lineAlignment)
                            .setLineSpacing(0f, 1f)
                            .setIncludePad(false)
                        list.add(JustifiedLine.NormalLine(builder.build(), lineTop))
                    }
                } else {
                    // Fallback для систем без полноценного Q-API
                    val lineWidth = layout.getLineWidth(i)
                    val extraSpace = availableWidth - lineWidth
                    
                    val letterSpacingToApply: Float
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
                    
                    val tempPaint = TextPaint(paint)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        tempPaint.letterSpacing = letterSpacingToApply
                    }
                    
                    val builder = StaticLayout.Builder.obtain(lineText, 0, lineText.length, tempPaint, availableWidth + 200)
                        .setAlignment(lineAlignment)
                        .setLineSpacing(0f, 1f)
                        .setIncludePad(false)
                        
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        builder.setBreakStrategy(android.graphics.text.LineBreaker.BREAK_STRATEGY_SIMPLE)
                        builder.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE)
                    }
                    
                    list.add(JustifiedLine.LetterSpacedLine(builder.build(), lineTop))
                }
            }
        }
        
        this.justifiedLines = list
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val currentLayout = layout ?: return
        
        canvas.save()
        canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())
        
        val lines = justifiedLines
        if (lines != null) {
            for (line in lines) {
                when (line) {
                    is JustifiedLine.NormalLine -> {
                        canvas.save()
                        canvas.translate(0f, line.lineTop)
                        line.singleLineLayout.draw(canvas)
                        canvas.restore()
                    }
                    is JustifiedLine.LetterSpacedLine -> {
                        canvas.save()
                        canvas.translate(0f, line.lineTop)
                        line.singleLineLayout.draw(canvas)
                        canvas.restore()
                    }
                    is JustifiedLine.WordSpacedLine -> {
                        canvas.save()
                        canvas.translate(0f, line.lineTop + line.lineBaseline)
                        var x = 0f
                        for (word in line.words) {
                            canvas.drawText(word, x, 0f, line.paint)
                            x += line.paint.measureText(word) + line.gapWidth
                        }
                        canvas.restore()
                    }
                }
            }
        } else {
            currentLayout.draw(canvas)
        }
        
        canvas.restore()
    }
}
