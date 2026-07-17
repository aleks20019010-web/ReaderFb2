package com.nightread.app.ui.customlayout

import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import kotlinx.coroutines.*
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

        // 1. Разбиваем текст на части (chunking) чтобы не вешать UI и ускорить открытие
        val totalLength = text.length
        
        // Переходим к по-настоящему инкрементальному построению
        val offsets = mutableListOf<Int>()
        offsets.add(0)

        // Запас в несколько пикселей
        val maxPageHeight = height - (paint.textSize * 0.15f).toInt().coerceAtLeast(6)
        
        var currentOffset = 0
        var pagesFound = 0
        val CHUNK_SIZE_CHARS = 10000 // Обрабатываем по 10к символов
        
        while (currentOffset < totalLength) {
            if (!isActive) break

            val end = minOf(currentOffset + CHUNK_SIZE_CHARS, totalLength)
            
            // Создаем макет только для текущего чанка
            val layout = createStaticLayout(text, currentOffset, end, paint, width, alignment, lineSpacingMultiplier, lineSpacingExtra, hyphenation, justify)
            val lineCount = layout.lineCount
            
            var currentLineIdx = 0
            while (currentLineIdx < lineCount) {
                var endLineIdx = currentLineIdx
                while (endLineIdx < lineCount) {
                    val nextLineIdx = endLineIdx + 1
                    val pageHeight = layout.getLineBottom(nextLineIdx - 1) - layout.getLineTop(currentLineIdx)
                    
                    if (pageHeight <= maxPageHeight) {
                        // Check for Form Feed (\u000C) to force a page break
                        val lineStart = currentOffset + layout.getLineStart(endLineIdx)
                        val lineEnd = currentOffset + layout.getLineEnd(endLineIdx)
                        
                        var hasFormFeed = false
                        for (i in lineStart until minOf(lineEnd, text.length)) {
                            if (text[i] == '\u000C') {
                                hasFormFeed = true
                                break
                            }
                        }
                        
                        if (hasFormFeed && endLineIdx > currentLineIdx) {
                            // If this line contains a page break and it's NOT the first line of the page,
                            // we stop here so this line starts on a new page.
                            break
                        }
                        
                        endLineIdx = nextLineIdx
                    } else {
                        break
                    }
                }

                if (endLineIdx == currentLineIdx) {
                    endLineIdx = currentLineIdx + 1
                }

                // Добавляем смещение, если мы нашли новую границу
                val lineStart = layout.getLineStart(endLineIdx - 1) // Просто для примера, нужно точнее
                // На самом деле нужно учитывать глобальное смещение
                val globalLineStart = currentOffset + layout.getLineStart(endLineIdx)
                
                if (globalLineStart > offsets.last() && globalLineStart < totalLength) {
                    offsets.add(globalLineStart)
                    pagesFound++
                    
                    // Сообщаем о прогрессе чаще
                    val offsetsCopy = ArrayList(offsets)
                    withContext(Dispatchers.Main) {
                        onProgress?.invoke(offsetsCopy, false)
                    }
                }

                currentLineIdx = endLineIdx
            }
            
            currentOffset = end
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

    // 9. Новая логика ленивой загрузки
    private val pagesCache = java.util.concurrent.ConcurrentHashMap<Int, StaticLayout>()
    private val renderQueue = java.util.concurrent.ConcurrentLinkedQueue<Int>()
    private var renderJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun isPageCached(pageIndex: Int): Boolean {
        return pagesCache.containsKey(pageIndex)
    }

    fun getPageLayout(
        pageIndex: Int,
        text: CharSequence,
        paint: TextPaint,
        width: Int,
        alignment: android.text.Layout.Alignment,
        lineSpacingMultiplier: Float,
        lineSpacingExtra: Float,
        hyphenation: Boolean,
        justify: Boolean,
        onLayoutReady: (StaticLayout) -> Unit
    ) {
        val cached = pagesCache[pageIndex]
        if (cached != null) {
            onLayoutReady(cached)
            return
        }

        // Render asynchronously
        scope.launch {
            val layout = createStaticLayout(text, 0, text.length, paint, width, alignment, lineSpacingMultiplier, lineSpacingExtra, hyphenation, justify)
            pagesCache[pageIndex] = layout
            withContext(Dispatchers.Main) {
                onLayoutReady(layout)
            }
        }
    }

    fun startBackgroundRendering(
        pagesLines: List<List<String>>,
        currentPage: Int,
        paint: TextPaint,
        width: Int,
        alignment: android.text.Layout.Alignment,
        lineSpacingMultiplier: Float,
        lineSpacingExtra: Float,
        hyphenation: Boolean,
        justify: Boolean
    ) {
        // Cancel previous rendering to prevent flooding and race conditions
        renderJob?.cancel()
        renderQueue.clear()
        
        // 1. Текущая (если нет)
        if (!pagesCache.containsKey(currentPage)) renderQueue.add(currentPage)
        
        // 2. Вперед (ближайшие 3)
        for (i in 1..3) {
            val p = currentPage + i
            if (p < pagesLines.size && !pagesCache.containsKey(p)) renderQueue.add(p)
        }
        
        // 3. Назад (ближайшие 2)
        for (i in 1..2) {
            val p = currentPage - i
            if (p >= 0 && !pagesCache.containsKey(p)) renderQueue.add(p)
        }
        
        // 4. Остальное
        for (i in pagesLines.indices) {
            if (!pagesCache.containsKey(i) && !renderQueue.contains(i)) renderQueue.add(i)
        }

        renderJob = scope.launch {
            renderNext(this, pagesLines, paint, width, alignment, lineSpacingMultiplier, lineSpacingExtra, hyphenation, justify)
        }
    }

    private suspend fun renderNext(
        scope: CoroutineScope,
        pagesLines: List<List<String>>,
        paint: TextPaint,
        width: Int,
        alignment: android.text.Layout.Alignment,
        lineSpacingMultiplier: Float,
        lineSpacingExtra: Float,
        hyphenation: Boolean,
        justify: Boolean
    ) {
        while (renderQueue.isNotEmpty()) {
            if (!scope.isActive) break // Check for cancellation
            val pageIdx = renderQueue.poll() ?: break
            if (pagesCache.containsKey(pageIdx)) continue
            
            val lines = pagesLines[pageIdx]
            val text = lines.joinToString("\n")
            
            val layout = createStaticLayout(text, 0, text.length, paint, width, alignment, lineSpacingMultiplier, lineSpacingExtra, hyphenation, justify)
            pagesCache[pageIdx] = layout
            
            // Allow other coroutines to run
            yield()
        }
    }

    /**
     * Быстрое получение макета для одной страницы текста, начиная с startOffset.
     */
    fun getFastPageLayout(
        text: CharSequence,
        startOffset: Int,
        width: Int,
        height: Int,
        paint: TextPaint,
        alignment: android.text.Layout.Alignment,
        lineSpacingMultiplier: Float,
        lineSpacingExtra: Float,
        hyphenation: Boolean,
        justify: Boolean
    ): Pair<StaticLayout, Int> {
        val safeWidth = (width - 4).coerceAtLeast(1)
        
        // Создаем временный Layout для определения того, сколько текста поместится
        val builder = StaticLayout.Builder.obtain(text, startOffset, text.length, paint, safeWidth)
            .setAlignment(alignment)
            .setLineSpacing(lineSpacingExtra, lineSpacingMultiplier)
            .setIncludePad(true)
        
        val layout = builder.build()
        val lineCount = layout.lineCount
        val lastVisibleLine = layout.getLineForVertical(height)
        
        // Получаем смещение начала следующей страницы
        val nextOffset = if (lastVisibleLine + 1 < lineCount) {
            layout.getLineStart(lastVisibleLine + 1)
        } else {
            text.length
        }
        
        // Создаем конечный Layout для текущей страницы
        val endOffset = layout.getLineEnd(lastVisibleLine)
        
        val finalLayout = createStaticLayout(text, startOffset, endOffset, paint, width, alignment, lineSpacingMultiplier, lineSpacingExtra, hyphenation, justify)
        
        return finalLayout to nextOffset
    }

    val hyphenationPatternsMap = java.util.WeakHashMap<android.graphics.Paint, List<String>>()
    val hyphenationEnabledMap = java.util.WeakHashMap<android.graphics.Paint, Boolean>()
    val minLeftHyphenLimitMap = java.util.WeakHashMap<android.graphics.Paint, Int>()
    val minRightHyphenLimitMap = java.util.WeakHashMap<android.graphics.Paint, Int>()
    val maxConsecutiveHyphensMap = java.util.WeakHashMap<android.graphics.Paint, Int>()

    private var isInitialized = false
    private var globalPatterns: List<String>? = null

    fun init(context: android.content.Context) {
        if (isInitialized) return
        try {
            val patterns = ArrayList<String>()
            val inputStream = context.resources.openRawResource(com.nightread.app.R.raw.hyph_ru_ru)
            java.io.BufferedReader(java.io.InputStreamReader(inputStream, "UTF-8")).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    patterns.add(line!!)
                }
            }
            globalPatterns = patterns
            isInitialized = true
            Log.d("PageSplitter", "Successfully loaded hyphenation patterns globally! Patterns count: ${patterns.size}")
        } catch (e: Exception) {
            Log.e("PageSplitter", "Error loading hyphenation patterns globally", e)
        }
    }

    fun loadHyphenationPatterns(context: android.content.Context, paint: android.text.TextPaint) {
        init(context)
        paint.hyphenationPatterns = globalPatterns
        paint.isHyphenationEnabled = true
        paint.minLeftHyphenLimit = 2
        paint.minRightHyphenLimit = 2
        paint.maxConsecutiveHyphens = 6
    }

    fun createStaticLayout(
        source: CharSequence, start: Int, end: Int, 
        paint: TextPaint, width: Int, 
        alignment: android.text.Layout.Alignment,
        lineSpacingMultiplier: Float, lineSpacingExtra: Float,
        hyphenation: Boolean,
        justify: Boolean = false
    ): StaticLayout {
        if (hyphenation && globalPatterns != null) {
            paint.hyphenationPatterns = globalPatterns
            paint.isHyphenationEnabled = true
            paint.minLeftHyphenLimit = 2
            paint.minRightHyphenLimit = 2
            paint.maxConsecutiveHyphens = 6
        }

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

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            if (justify) {
                builder.setJustificationMode(android.text.Layout.JUSTIFICATION_MODE_INTER_WORD)
            } else {
                builder.setJustificationMode(android.text.Layout.JUSTIFICATION_MODE_NONE)
            }
        }
        
        return builder.build()
    }
}

var android.graphics.Paint.hyphenationPatterns: List<String>?
    get() = PageSplitter.hyphenationPatternsMap[this]
    set(value) {
        if (value != null) {
            PageSplitter.hyphenationPatternsMap[this] = value
        } else {
            PageSplitter.hyphenationPatternsMap.remove(this)
        }
    }

var android.graphics.Paint.isHyphenationEnabled: Boolean
    get() = PageSplitter.hyphenationEnabledMap[this] ?: false
    set(value) {
        PageSplitter.hyphenationEnabledMap[this] = value
    }

var android.graphics.Paint.minLeftHyphenLimit: Int
    get() = PageSplitter.minLeftHyphenLimitMap[this] ?: 2
    set(value) {
        PageSplitter.minLeftHyphenLimitMap[this] = value
    }

var android.graphics.Paint.minRightHyphenLimit: Int
    get() = PageSplitter.minRightHyphenLimitMap[this] ?: 2
    set(value) {
        PageSplitter.minRightHyphenLimitMap[this] = value
    }

var android.graphics.Paint.maxConsecutiveHyphens: Int
    get() = PageSplitter.maxConsecutiveHyphensMap[this] ?: 6
    set(value) {
        PageSplitter.maxConsecutiveHyphensMap[this] = value
    }

