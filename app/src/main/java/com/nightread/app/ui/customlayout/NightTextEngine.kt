package com.nightread.app.ui.customlayout

import android.graphics.Canvas
import android.text.TextPaint
import android.text.Layout
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.yield

// ==========================================
// 1. HYPHENATOR (АЛГОРИТМ ПЕРЕНОСОВ)
// ==========================================
object Hyphenator {
    private val vowels = setOf('а', 'е', 'ё', 'и', 'о', 'у', 'ы', 'э', 'ю', 'я')
    private val signs = setOf('ь', 'ъ', 'й')

    private fun isVowel(c: Char) = vowels.contains(c.lowercaseChar())
    private fun isSign(c: Char) = signs.contains(c.lowercaseChar())
    private fun isConsonant(c: Char) = c.lowercaseChar() in 'а'..'я' && !isVowel(c) && !isSign(c)

    fun getHyphenationPoints(word: String): List<Int> {
        val points = mutableListOf<Int>()
        if (word.length <= 3) return points

        for (i in 1 until word.length - 2) {
            val c1 = word[i - 1]
            val c2 = word[i]
            val c3 = word[i + 1]

            // Не оставлять одну букву в начале и не переносить одну в конце
            if (i < 2 || i > word.length - 2) continue
            // Не отрываем знаки от предыдущей буквы
            if (isSign(c3)) continue
            
            if (isSign(c2)) {
                points.add(i + 1) // Перенос после знака (маль-чик, подъ-езд)
                continue
            }

            // Гласная - Согласная - Гласная (мо-ло-ко)
            if (isVowel(c1) && isConsonant(c2) && isVowel(c3)) {
                points.add(i)
            }
            // Гласная - Согласная - Согласная (кар-та)
            else if (isVowel(c1) && isConsonant(c2) && isConsonant(c3)) {
                if (i + 2 < word.length && isVowel(word[i + 2])) {
                    points.add(i + 1)
                }
            }
            // Стечение согласных
            else if (isConsonant(c1) && isConsonant(c2) && isVowel(c3)) {
                if (i - 2 >= 0 && isVowel(word[i - 2])) {
                    points.add(i)
                }
            }
        }
        return points
    }
}

// ==========================================
// 2. TEXT MEASURER (ИЗМЕРИТЕЛЬ ТЕКСТА)
// ==========================================
class TextMeasurer(val paint: TextPaint) {
    fun measureText(text: CharSequence, start: Int = 0, end: Int = text.length): Float {
        return paint.measureText(text, start, end)
    }

    val hyphenWidth: Float
        get() = paint.measureText("-")
        
    fun getLineHeight(lineSpacingMultiplier: Float, lineSpacingExtra: Float): Float {
        val fm = paint.fontMetrics
        return (fm.descent - fm.ascent) * lineSpacingMultiplier + lineSpacingExtra
    }
}

// ==========================================
// 3. LINE BREAKER (РАЗБИВКА НА СТРОКИ)
// ==========================================
data class TextLine(
    val text: CharSequence,
    val width: Float,
    val hasHyphen: Boolean,
    val isParagraphEnd: Boolean = false,
    val isTitle: Boolean = false // Для потенциального расширения
)

class LineBreaker(private val measurer: TextMeasurer) {
    fun breakText(text: CharSequence, maxWidth: Float): List<TextLine> {
        val lines = mutableListOf<TextLine>()
        if (text.isEmpty()) return lines

        val paragraphs = text.split('\n', '\u000C')
        
        for ((index, paragraph) in paragraphs.withIndex()) {
            if (paragraph.isEmpty()) {
                lines.add(TextLine("", 0f, hasHyphen = false, isParagraphEnd = true))
                continue
            }

            // Улучшенная разбивка: сохраняем пробелы
            val tokens = paragraph.split("(?<=\\s)|(?=\\s)".toRegex()).filter { it.isNotEmpty() }
            var currentLine = StringBuilder()
            var currentWidth = 0f
            
            for (token in tokens) {
                val tokenWidth = measurer.measureText(token)
                
                if (currentWidth + tokenWidth <= maxWidth) {
                    currentLine.append(token)
                    currentWidth += tokenWidth
                } else {
                    if (token.isNotBlank()) {
                        val hyphenPoints = Hyphenator.getHyphenationPoints(token)
                        var bestSplit = -1
                        var bestWidth = 0f
                        
                        for (point in hyphenPoints) {
                            val part = token.substring(0, point)
                            val partWidth = measurer.measureText(part)
                            if (currentWidth + partWidth + measurer.hyphenWidth <= maxWidth) {
                                bestSplit = point
                                bestWidth = partWidth
                            }
                        }
                        
                        if (bestSplit != -1) {
                            currentLine.append(token.substring(0, bestSplit))
                            lines.add(TextLine(currentLine.toString().trimEnd(), currentWidth + bestWidth, hasHyphen = true))
                            
                            currentLine = StringBuilder(token.substring(bestSplit))
                            currentWidth = measurer.measureText(currentLine.toString())
                            continue
                        }
                    }
                    
                    if (currentLine.isNotBlank()) {
                        lines.add(TextLine(currentLine.toString().trimEnd(), currentWidth, hasHyphen = false))
                    }
                    
                    currentLine = StringBuilder(token.trimStart())
                    currentWidth = measurer.measureText(currentLine.toString())
                }
            }
            
            if (currentLine.isNotEmpty()) {
                val isLast = index == paragraphs.lastIndex
                lines.add(TextLine(currentLine.toString().trimEnd(), currentWidth, hasHyphen = false, isParagraphEnd = !isLast))
            }
        }
        
        return lines
    }
}

// ==========================================
// 4. CUSTOM TEXT LAYOUT (МАКЕТ ТЕКСТА)
// ==========================================
class CustomTextLayout(
    val lines: List<TextLine>,
    val paint: TextPaint,
    val width: Int,
    val height: Int,
    val lineSpacingMultiplier: Float,
    val lineSpacingExtra: Float,
    val alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
) {
    fun draw(canvas: Canvas, x: Float, y: Float) {
        val fm = paint.fontMetrics
        var currentY = y - fm.ascent 
        val lineHeight = (fm.descent - fm.ascent) * lineSpacingMultiplier + lineSpacingExtra
        
        for (line in lines) {
            val textToDraw = if (line.hasHyphen) line.text.toString() + "-" else line.text.toString()
            
            // Расчет x с учетом выравнивания
            val startX = when (alignment) {
                Layout.Alignment.ALIGN_CENTER -> x + (width - line.width) / 2f
                Layout.Alignment.ALIGN_OPPOSITE -> x + width - line.width
                else -> x
            }
            
            canvas.drawText(textToDraw, startX, currentY, paint)
            currentY += lineHeight
        }
    }
}

// ==========================================
// 5. PAGE SPLITTER (ПАГИНАТОР С БИНАРНЫМ ПОИСКОМ)
// ==========================================
class PageSplitter(
    private val paint: TextPaint,
    private val width: Int,
    private val height: Int,
    private val lineSpacingMultiplier: Float = 1.0f,
    private val lineSpacingExtra: Float = 0f
) {
    private val measurer = TextMeasurer(paint)
    private val lineBreaker = LineBreaker(measurer)
    
    /**
     * Поиск конца страницы с использованием бинарного поиска по ВЫСОТЕ.
     * Возвращает индекс конца страницы.
     */
    suspend fun findPageEnd(text: CharSequence, start: Int): Int {
        var low = start + 1
        var high = text.length
        var bestEnd = start
        
        val lineHeight = measurer.getLineHeight(lineSpacingMultiplier, lineSpacingExtra)
        val maxLinesPerPage = maxOf(1, (height / lineHeight).toInt())
        
        // Оптимизация (сужаем окно для быстрого поиска)
        val avgCharWidth = measurer.measureText("а")
        val charsPerLine = if (avgCharWidth > 0) (width / avgCharWidth).toInt() else 40
        val estimatedChars = maxLinesPerPage * charsPerLine
        low = minOf(text.length, start + estimatedChars / 2)
        // Для high даем больший запас или до конца текста
        high = minOf(text.length, start + estimatedChars * 4)
        if (high < text.length && high < start + 5000) {
             high = minOf(text.length, start + 5000)
        }

        while (low <= high) {
            if (!kotlinx.coroutines.currentCoroutineContext().isActive) break
            
            val mid = low + (high - low) / 2
            val chunk = text.subSequence(start, mid)
            val lines = lineBreaker.breakText(chunk, width.toFloat())
            
            // Расчет реальной высоты текста вместо количества строк
            val totalHeight = lines.size * lineHeight
            
            if (totalHeight <= height) {
                bestEnd = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        
        // Если слово оборвалось, откатываемся к началу слова
        if (bestEnd < text.length && !text[bestEnd].isWhitespace() && bestEnd > 0 && !text[bestEnd - 1].isWhitespace()) {
            var spaceIdx = bestEnd
            while (spaceIdx > start && !text[spaceIdx].isWhitespace()) {
                spaceIdx--
            }
            if (spaceIdx > start) {
                bestEnd = spaceIdx + 1
            }
        }

        // Учитываем явные разрывы страниц
        val pageBreakIdx = text.indexOf('\u000C', start)
        if (pageBreakIdx != -1 && pageBreakIdx < bestEnd) {
            return pageBreakIdx + 1
        }
        
        return bestEnd
    }
}

// ==========================================
// 6. LAYOUT CACHE (LRU-КЕШ)
// ==========================================
object LayoutCache {
    private val pagesCache = LruCache<String, LruCache<Int, CustomTextLayout>>(3)
    private val offsetsCache = LruCache<String, List<Int>>(3)
    
    fun getPage(configKey: String, offset: Int): CustomTextLayout? {
        return pagesCache.get(configKey)?.get(offset)
    }
    
    fun putPage(configKey: String, offset: Int, layout: CustomTextLayout) {
        var pageMap = pagesCache.get(configKey)
        if (pageMap == null) {
            pageMap = LruCache(50)
            pagesCache.put(configKey, pageMap)
        }
        pageMap.put(offset, layout)
    }
    
    fun getOffsets(configKey: String): List<Int>? = offsetsCache.get(configKey)
    
    fun putOffsets(configKey: String, offsets: List<Int>) {
        offsetsCache.put(configKey, offsets)
    }
    
    fun clear() {
        pagesCache.evictAll()
        offsetsCache.evictAll()
    }
}

// ==========================================
// 7. TEXT LAYOUT BUILDER (ОСНОВНОЕ API)
// ==========================================
class TextLayoutBuilder {
    private var text: CharSequence = ""
    private var width: Int = 0
    private var height: Int = 0
    private var paint: TextPaint = TextPaint()
    private var lineSpacingMultiplier: Float = 1.0f
    private var lineSpacingExtra: Float = 0f
    private var alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL

    fun setText(text: CharSequence) = apply { this.text = text }
    fun setWidth(width: Int) = apply { this.width = width }
    fun setHeight(height: Int) = apply { this.height = height }
    fun setPaint(paint: TextPaint) = apply { this.paint = paint }
    fun setAlignment(alignment: Layout.Alignment) = apply { this.alignment = alignment }
    fun setLineSpacing(extra: Float, multiplier: Float) = apply {
        this.lineSpacingExtra = extra
        this.lineSpacingMultiplier = multiplier
    }
    
    val configKey: String
        get() = "${text.length}_${width}_${height}_${paint.textSize}_${paint.typeface?.hashCode()}_${lineSpacingMultiplier}_${alignment.name}"

    /**
     * Возвращает список смещений (начало каждой страницы).
     */
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

        val offsets = mutableListOf<Int>()
        val splitter = PageSplitter(paint, width, height, lineSpacingMultiplier, lineSpacingExtra)
        
        var currentOffset = 0
        var pagesFound = 0
        
        while (currentOffset < text.length) {
            if (!isActive) break
            
            offsets.add(currentOffset)
            val endOffset = splitter.findPageEnd(text, currentOffset)
            if (endOffset <= currentOffset) {
                currentOffset++ // Защита от зацикливания
            } else {
                currentOffset = endOffset
            }
            
            pagesFound++
            if (pagesFound == 1 || pagesFound % 10 == 0) {
                val currentOffsetsCopy = ArrayList(offsets)
                withContext(Dispatchers.Main) {
                    onProgress?.invoke(currentOffsetsCopy, false)
                }
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

    /**
     * Создает макет для конкретной страницы по смещению.
     */
    fun buildPageLayout(offset: Int, endOffset: Int): CustomTextLayout {
        val cached = LayoutCache.getPage(configKey, offset)
        if (cached != null) return cached
        
        val chunk = text.subSequence(offset, endOffset)
        val measurer = TextMeasurer(paint)
        val breaker = LineBreaker(measurer)
        val lines = breaker.breakText(chunk, width.toFloat())
        
        val layout = CustomTextLayout(lines, paint, width, height, lineSpacingMultiplier, lineSpacingExtra, alignment)
        LayoutCache.putPage(configKey, offset, layout)
        return layout
    }
}
