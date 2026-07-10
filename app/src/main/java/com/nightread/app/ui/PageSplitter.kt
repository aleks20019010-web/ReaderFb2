package com.nightread.app.ui

import android.graphics.Color
import android.graphics.Typeface
import android.text.*
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive

object PageSplitter {
    private const val TAG = "PageSplitter"

    data class PageResult(
        var pages: MutableList<CharSequence> = mutableListOf(),
        var offsets: MutableList<Int> = mutableListOf(),
        var isFinished: Boolean = false
    )

    /**
     * Обрабатывает теги [CHAPTER]...[/CHAPTER]: скрывает сами теги,
     * а текст между ними делает заголовком (жирный, крупнее, по центру).
     */
    fun formatChapterSpans(text: CharSequence, basePaintSize: Float): CharSequence {
        if (text.isEmpty() || !text.contains("[CHAPTER]")) return text

        val spannable = SpannableStringBuilder(text)
        val str = spannable.toString()
        var lastIdx = 0

        while (true) {
            val startTag = str.indexOf("[CHAPTER]", lastIdx)
            if (startTag == -1) break

            val endTag = str.indexOf("[/CHAPTER]", startTag)
            if (endTag == -1) break

            // Скрываем [CHAPTER]
            spannable.setSpan(
                AbsoluteSizeSpan(0),
                startTag,
                startTag + "[CHAPTER]".length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                ForegroundColorSpan(Color.TRANSPARENT),
                startTag,
                startTag + "[CHAPTER]".length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            // Скрываем [/CHAPTER]
            spannable.setSpan(
                AbsoluteSizeSpan(0),
                endTag,
                endTag + "[/CHAPTER]".length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                ForegroundColorSpan(Color.TRANSPARENT),
                endTag,
                endTag + "[/CHAPTER]".length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            // Стилизуем заголовок главы
            val titleStart = startTag + "[CHAPTER]".length
            val titleEnd = endTag
            if (titleEnd > titleStart) {
                spannable.setSpan(
                    AbsoluteSizeSpan((basePaintSize * 1.3f).toInt()),
                    titleStart,
                    titleEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    titleStart,
                    titleEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
                    titleStart,
                    titleEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            lastIdx = endTag + "[/CHAPTER]".length
        }

        return spannable
    }

    /**
     * Находит страницу, содержащую targetOffset.
     * Используется, например, для быстрого перехода к позиции чтения.
     */
    suspend fun getPageForOffset(
        text: CharSequence,
        targetOffset: Int,
        availableWidth: Int,
        availableHeight: Int,
        paint: TextPaint,
        lineSpacing: Float,
        alignment: String = "justify",
        hyphenationEnabled: Boolean = false
    ): Pair<Int, CharSequence> = withContext(Dispatchers.Default) {
        if (text.isEmpty() || availableWidth <= 0 || availableHeight <= 0) {
            return@withContext Pair(0, "Документ пуст.")
        }

        var start = targetOffset
        // Ищем начало текущего абзаца (до ближайшего \n или \u000C)
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

        // Строим макет только для куска текста, чтобы найти, сколько строк влезает
        val tempLayout = StaticLayout.Builder.obtain(
            text,
            start,
            (start + 8000).coerceAtMost(textLength),
            paint,
            availableWidth
        )
            .setAlignment(alignmentVal)
            .setLineSpacing(0f, lineSpacing)
            .setIncludePad(false)
            .setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED)
            // Ключевое исправление: переносы включаются только если hyphenationEnabled == true
            .setHyphenationFrequency(
                if (hyphenationEnabled) Layout.HYPHENATION_FREQUENCY_FULL
                else Layout.HYPHENATION_FREQUENCY_NONE
            )
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

        return@withContext Pair(start, text.subSequence(start, end))
    }

    /**
     * Прогрессивная разбивка текста на страницы.
     * Возвращает результат через onProgress, чтобы UI мог показывать прогресс.
     */
    suspend fun splitTextProgressive(
        text: CharSequence,
        availableWidth: Int,
        availableHeight: Int,
        paint: TextPaint,
        lineSpacing: Float,
        alignment: String = "justify",
        hyphenationEnabled: Boolean = false,
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

        val formattedText = text // Если нужна предварительная обработка — делай её до вызова этого метода
        var start = 0
        val textLength = formattedText.length

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

            // Увеличиваем кусок текста, пока он целиком влезает по высоте
            while (true) {
                measureEnd = (start + chunkSize).coerceAtMost(textLength)
                tempLayout = StaticLayout.Builder.obtain(
                    formattedText,
                    start,
                    measureEnd,
                    paint,
                    availableWidth
                )
                    .setAlignment(alignmentVal)
                    .setLineSpacing(0f, lineSpacing)
                    .setIncludePad(false)
                    .setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED)
                    // Переносы управляются флагом hyphenationEnabled
                    .setHyphenationFrequency(
                        if (hyphenationEnabled) Layout.HYPHENATION_FREQUENCY_FULL
                        else Layout.HYPHENATION_FREQUENCY_NONE
                    )
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

            // Считаем, сколько строк реально влезает в availableHeight
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

            // Если внутри куска есть разрыв главы (\u000C), обрезаем страницу ровно перед ним
            var foundChapterBreakIdx = -1
            for (idx in start until end.coerceAtMost(textLength)) {
                if (formattedText[idx] == '\u000C') {
                    foundChapterBreakIdx = idx
                    break
                }
            }

            if (foundChapterBreakIdx != -1) {
                end = foundChapterBreakIdx
                result.pages.add(formattedText.subSequence(start, end))
                start = foundChapterBreakIdx + 1
            } else {
                result.pages.add(formattedText.subSequence(start, end))
                start = end
            }

            pagesFound++

            // Отчитываемся о прогрессе каждые 10 страниц или на первой странице
            if (pagesFound == 1 || pagesFound % 10 == 0) {
                withContext(Dispatchers.Main) {
                    onProgress(PageResult(ArrayList(result.pages), ArrayList(result.offsets), false))
                }
            }
        }

        result.isFinished = true
        withContext(Dispatchers.Main) {
            onProgress(PageResult(ArrayList(result.pages), ArrayList(result.offsets), true))
        }
    }

    /**
     * Синхронная (по факту — корутинная) разбивка без прогресса.
     * Просто оборачивает splitTextProgressive.
     */
    suspend fun splitText(
        text: CharSequence,
        availableWidth: Int,
        availableHeight: Int,
        paint: TextPaint,
        lineSpacing: Float,
        alignment: String = "justify",
        hyphenationEnabled: Boolean = false
    ): PageResult {
        var finalResult = PageResult()
        splitTextProgressive(
            text = text,
            availableWidth = availableWidth,
            availableHeight = availableHeight,
            paint = paint,
            lineSpacing = lineSpacing,
            alignment = alignment,
            hyphenationEnabled = hyphenationEnabled,
            onProgress = { result ->
                if (result.isFinished) {
                    finalResult = result
                }
            }
        )
        return finalResult
    }
}
