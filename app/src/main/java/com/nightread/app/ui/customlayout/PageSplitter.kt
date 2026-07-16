package com.nightread.app.ui.customlayout

import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/**
 * PageSplitter — класс для гибридного разбиения книги на страницы.
 * 
 * Логика работы:
 * 1. Разбивает весь текст на строки через StaticLayout (один раз).
 * 2. Группирует строки в страницы по фиксированному количеству строк.
 * 3. Корректирует границы страниц, стараясь не разрывать абзацы и предложения.
 * 4. Заполняет последнюю строку страницы (если ее ширина < 30%), подтягивая слова со следующей страницы.
 * 
 * Пример использования:
 * ```kotlin
 * val pageSplitter = PageSplitter
 * val linesPerPage = pageSplitter.getLinesPerPage(height, paint, multiplier, extra)
 * val pages = pageSplitter.groupLinesIntoPages(lines, minLinesPerPage = 3, maxLinesPerPage = linesPerPage)
 * ```
 */
object PageSplitter {
    private val linesCache = mutableMapOf<String, Int>()

    /**
     * Рассчитывает статическое количество строк, помещающихся на одной странице,
     * исходя из высоты экрана, шрифта и межстрочных интервалов.
     */
    fun getLinesPerPage(height: Int, paint: TextPaint, lineSpacingMultiplier: Float, lineSpacingExtra: Float): Int {
        val key = "${height}_${paint.textSize}_${lineSpacingMultiplier}_${lineSpacingExtra}"
        
        return linesCache.getOrPut(key) {
            val fm = paint.fontMetrics
            val lineHeight = (fm.descent - fm.ascent) * lineSpacingMultiplier + lineSpacingExtra
            // Вычитаем запас в 12 пикселей для предотвращения обрезания последней строки из-за внутренних отступов шрифта
            val safeHeight = height - 12
            val lines = (safeHeight / lineHeight).toInt()
            val finalLines = maxOf(3, lines)
            Log.d("PageSplitter", "Calculated linesPerPage: $finalLines for height: $height, lineHeight: $lineHeight")
            finalLines
        }
    }

    /**
     * Проверяет, является ли строка началом нового абзаца.
     */
    private fun isParagraphStart(line: String, prevLine: String?): Boolean {
        if (line.isEmpty()) return true
        // Если предыдущая строка заканчивается переносом строки, текущая начинает абзац
        if (prevLine != null && (prevLine.endsWith("\n") || prevLine.endsWith("\r") || prevLine.endsWith("\u000C"))) {
            return true
        }
        // Если строка начинается с пробельных символов (красная строка / отступ)
        if (line.startsWith(" ") || line.startsWith("\t") || line.startsWith("\u00A0")) {
            return true
        }
        return false
    }

    /**
     * Проверяет, заканчивается ли строка концом предложения.
     */
    private fun isSentenceEnd(line: String): Boolean {
        val trimmed = line.trimEnd()
        if (trimmed.isEmpty()) return false
        val lastChar = trimmed.last()
        return lastChar == '.' || lastChar == '!' || lastChar == '?' || 
               trimmed.endsWith(".\"") || trimmed.endsWith("!\"") || trimmed.endsWith("?\"") ||
               trimmed.endsWith("»") || trimmed.endsWith(")")
    }

    /**
     * Корректирует границы страниц, стараясь не разрывать абзацы и предложения.
     * Возвращает новый скорректированный индекс конца страницы.
     */
    fun adjustPageBoundary(lines: List<String>, start: Int, end: Int): Int {
        val tentativeEnd = minOf(end, lines.size)
        // Если на странице слишком мало строк, не корректируем, чтобы не нарушать minLinesPerPage
        if (tentativeEnd <= start + 3) return tentativeEnd
        
        // Проверяем, является ли текущая граница естественным концом абзаца
        val nextLine = if (tentativeEnd < lines.size) lines[tentativeEnd] else null
        val currentLine = lines[tentativeEnd - 1]
        if (nextLine == null || isParagraphStart(nextLine, currentLine)) {
            return tentativeEnd
        }
        
        // 1. Пытаемся найти границу абзаца, двигаясь назад (не дальше чем до start + 3 строк)
        for (i in tentativeEnd downTo start + 3) {
            val next = if (i < lines.size) lines[i] else null
            val curr = lines[i - 1]
            if (next == null || isParagraphStart(next, curr)) {
                return i
            }
        }
        
        // 2. Если абзац слишком длинный, пытаемся найти конец предложения, двигаясь назад
        for (i in tentativeEnd downTo start + 3) {
            val curr = lines[i - 1]
            if (isSentenceEnd(curr)) {
                return i
            }
        }
        
        // 3. Если назад не нашли предложений, ищем конец предложения вперед (не более чем на 2 строки)
        val limit = minOf(tentativeEnd + 2, lines.size)
        for (i in tentativeEnd + 1..limit) {
            val curr = lines[i - 1]
            if (isSentenceEnd(curr)) {
                return i
            }
        }
        
        return tentativeEnd
    }

    /**
     * Заполняет последнюю строку страницы словами из следующей страницы, 
     * если ширина последней строки меньше minLastLineWidthPercent.
     */
    fun fillLastLine(
        pageLines: List<String>, 
        nextLines: List<String>,
        paint: TextPaint,
        width: Int,
        minLastLineWidthPercent: Float = 0.3f
    ): List<String> {
        if (pageLines.isEmpty() || nextLines.isEmpty() || width <= 0) return pageLines
        
        val lastLine = pageLines.last()
        val lastLineWidth = paint.measureText(lastLine.trimEnd())
        val targetMin = width * minLastLineWidthPercent
        
        // Если ширина последней строки уже достаточная, ничего не делаем
        if (lastLineWidth >= targetMin) return pageLines
        
        // Берем первую строку следующей страницы и пытаемся перетащить слова
        val nextLine = nextLines.first()
        val words = nextLine.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return pageLines
        
        val newPageLines = pageLines.toMutableList()
        var currentLastLine = lastLine
        val pulledWords = mutableListOf<String>()
        
        for (word in words) {
            val separator = if (currentLastLine.endsWith("-") || currentLastLine.endsWith("\u00AD")) "" else " "
            val testLine = currentLastLine + separator + word
            if (paint.measureText(testLine.trimEnd()) <= width) {
                currentLastLine = testLine
                pulledWords.add(word)
            } else {
                break
            }
        }
        
        if (pulledWords.isNotEmpty()) {
            newPageLines[newPageLines.size - 1] = currentLastLine
        }
        
        return newPageLines
    }

    /**
     * Группирует строки в страницы, корректируя границы и перенося короткие последние строки на следующую страницу.
     */
    fun groupLinesIntoPages(
        lines: List<String>,
        minLinesPerPage: Int = 3,
        maxLinesPerPage: Int = 15,
        minLastLineWidthPercent: Float = 0.3f,
        paint: TextPaint? = null,
        width: Int = 0
    ): List<List<String>> {
        val mutableLines = lines.toMutableList()
        val pages = mutableListOf<List<String>>()
        var current = 0
        
        while (current < mutableLines.size) {
            val tentativeEnd = minOf(current + maxLinesPerPage, mutableLines.size)
            var adjustedEnd = adjustPageBoundary(mutableLines, current, tentativeEnd)
            
            // Гарантируем минимальное количество строк на странице (кроме конца текста)
            if (adjustedEnd - current < minLinesPerPage && tentativeEnd < mutableLines.size) {
                adjustedEnd = tentativeEnd
            }
            
            // 7. Если страница заканчивается на короткой строке (< 30% ширины) - перенести ее на следующую
            if (paint != null && width > 0) {
                var pageLinesCount = adjustedEnd - current
                while (pageLinesCount > minLinesPerPage) {
                    val lastLineIdx = current + pageLinesCount - 1
                    val lastLineText = mutableLines[lastLineIdx]
                    val lastLineWidth = paint.measureText(lastLineText.trimEnd())
                    val targetMin = width * minLastLineWidthPercent
                    
                    if (lastLineWidth < targetMin) {
                        adjustedEnd--
                        pageLinesCount--
                    } else {
                        break
                    }
                }
            }
            
            val pageLines = mutableLines.subList(current, adjustedEnd)
            pages.add(pageLines.toList())
            current = adjustedEnd
        }
        
        // 8. Если на последней странице слишком мало строк - объединяем ее с предпоследней
        if (pages.size > 1 && pages.last().size < minLinesPerPage) {
            val lastPage = pages.removeAt(pages.size - 1)
            val prevPage = pages.removeAt(pages.size - 1)
            pages.add(prevPage + lastPage)
        }
        
        return pages
    }

    /**
     * Главный асинхронный метод для расчета пагинации всей книги/главы.
     * Возвращает список символьных смещений начала каждой страницы для ReaderViewModel.
     */
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
        onProgress: ((List<Int>, Boolean) -> Unit)? = null,
        justify: Boolean = false
    ): List<Int> = withContext(Dispatchers.Default) {
        if (text.isEmpty() || width <= 0 || height <= 0) {
            val res = listOf(0)
            withContext(Dispatchers.Main) { onProgress?.invoke(res, true) }
            return@withContext res
        }

        // Проверяем наличие результатов в кэше
        val cached = LayoutCache.getOffsets(configKey)
        if (cached != null) {
            withContext(Dispatchers.Main) { onProgress?.invoke(cached, true) }
            return@withContext cached
        }

        paint.letterSpacing = letterSpacing
        paint.textLocale = java.util.Locale("ru", "RU")

        // 1. Разбиваем весь текст на строки через StaticLayout (один раз)
        val layout = createStaticLayout(text, 0, text.length, paint, width, alignment, lineSpacingMultiplier, lineSpacingExtra, hyphenation, justify)
        val lineCount = layout.lineCount
        val lineStartOffsets = ArrayList<Int>()
        for (i in 0 until lineCount) {
            lineStartOffsets.add(layout.getLineStart(i))
        }

        val offsets = mutableListOf<Int>()
        offsets.add(0)

        var currentLineIdx = 0
        var pagesFound = 0

        // Запас в несколько пикселей, чтобы гарантировать, что последний символ/строка не будут обрезаны из-за погрешностей рендеринга
        val maxPageHeight = height - (paint.textSize * 0.15f).toInt().coerceAtLeast(6)

        while (currentLineIdx < lineCount) {
            if (!isActive) break

            var endLineIdx = currentLineIdx
            while (endLineIdx < lineCount) {
                val nextLineIdx = endLineIdx + 1
                val pageHeight = layout.getLineBottom(nextLineIdx - 1) - layout.getLineTop(currentLineIdx)
                if (pageHeight <= maxPageHeight) {
                    endLineIdx = nextLineIdx
                } else {
                    break
                }
            }

            // Обеспечиваем продвижение вперед хотя бы на одну строку, чтобы избежать бесконечного цикла
            if (endLineIdx == currentLineIdx) {
                endLineIdx = currentLineIdx + 1
            }

            // Добавляем смещение для следующей страницы
            if (endLineIdx < lineCount) {
                val nextPageOffset = lineStartOffsets[endLineIdx]
                if (nextPageOffset < text.length) {
                    offsets.add(nextPageOffset)
                    pagesFound++
                    
                    // Периодически сообщаем о прогрессе для плавного интерфейса
                    if (pagesFound % 20 == 0) {
                        val offsetsCopy = ArrayList(offsets)
                        withContext(Dispatchers.Main) {
                            onProgress?.invoke(offsetsCopy, false)
                        }
                    }
                }
            }

            currentLineIdx = endLineIdx
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

    fun createStaticLayout(
        source: CharSequence, start: Int, end: Int, 
        paint: TextPaint, width: Int, 
        alignment: android.text.Layout.Alignment,
        lineSpacingMultiplier: Float, lineSpacingExtra: Float,
        hyphenation: Boolean,
        justify: Boolean = false
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

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (justify) {
                builder.setJustificationMode(android.text.Layout.JUSTIFICATION_MODE_INTER_WORD)
            } else {
                builder.setJustificationMode(android.text.Layout.JUSTIFICATION_MODE_NONE)
            }
        }
        
        return builder.build()
    }
}
