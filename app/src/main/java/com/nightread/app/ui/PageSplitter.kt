package com.nightread.app.ui

import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.Spanned
import android.text.SpannableStringBuilder
import android.text.style.AbsoluteSizeSpan
import android.text.style.StyleSpan
import android.text.style.AlignmentSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.SuperscriptSpan
import android.text.style.RelativeSizeSpan
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
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

    fun formatAllSpans(
        context: android.content.Context? = null,
        text: CharSequence,
        basePaintSize: Float,
        pageStartOffset: Int? = null,
        onNoteClick: ((String) -> Unit)? = null
    ): CharSequence {
        if (text.isEmpty()) return text
        
        val spannable = SpannableStringBuilder(text)
        val str = spannable.toString()
        
        // 1. Format [CHAPTER]...[/CHAPTER]
        var lastIdx = 0
        while (true) {
            val startTag = str.indexOf("[CHAPTER]", lastIdx)
            if (startTag == -1) break
            val endTag = str.indexOf("[/CHAPTER]", startTag)
            if (endTag == -1) break
            
            // Hide [CHAPTER]
            spannable.setSpan(AbsoluteSizeSpan(0), startTag, startTag + "[CHAPTER]".length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(ForegroundColorSpan(Color.TRANSPARENT), startTag, startTag + "[CHAPTER]".length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            
            // Hide [/CHAPTER]
            spannable.setSpan(AbsoluteSizeSpan(0), endTag, endTag + "[/CHAPTER]".length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(ForegroundColorSpan(Color.TRANSPARENT), endTag, endTag + "[/CHAPTER]".length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            
            val titleStart = startTag + "[CHAPTER]".length
            val titleEnd = endTag
            if (titleEnd > titleStart) {
                spannable.setSpan(AbsoluteSizeSpan((basePaintSize * 1.5f).toInt()), titleStart, titleEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(StyleSpan(Typeface.BOLD), titleStart, titleEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                var alignStart = startTag
                if (alignStart > 0 && spannable[alignStart - 1] == '\u000C') {
                    alignStart--
                }
                spannable.setSpan(AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), alignStart, endTag + "[/CHAPTER]".length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            lastIdx = endTag + "[/CHAPTER]".length
        }
        
        // 2. Format [CITE]...[/CITE]
        lastIdx = 0
        while (true) {
            val startTag = str.indexOf("[CITE]", lastIdx)
            if (startTag == -1) break
            val endTag = str.indexOf("[/CITE]", startTag)
            if (endTag == -1) break
            
            // Hide tags
            spannable.setSpan(AbsoluteSizeSpan(0), startTag, startTag + "[CITE]".length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(ForegroundColorSpan(Color.TRANSPARENT), startTag, startTag + "[CITE]".length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(AbsoluteSizeSpan(0), endTag, endTag + "[/CITE]".length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(ForegroundColorSpan(Color.TRANSPARENT), endTag, endTag + "[/CITE]".length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            
            val contentStart = startTag + "[CITE]".length
            val contentEnd = endTag
            if (contentEnd > contentStart) {
                spannable.setSpan(StyleSpan(Typeface.ITALIC), contentStart, contentEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(LeadingMarginSpan.Standard(60), contentStart, contentEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            lastIdx = endTag + "[/CITE]".length
        }

        // 3. Format [SUP]...[/SUP]
        lastIdx = 0
        while (true) {
            val startTag = str.indexOf("[SUP]", lastIdx)
            if (startTag == -1) break
            val endTag = str.indexOf("[/SUP]", startTag)
            if (endTag == -1) break
            
            // Hide tags
            spannable.setSpan(AbsoluteSizeSpan(0), startTag, startTag + "[SUP]".length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(ForegroundColorSpan(Color.TRANSPARENT), startTag, startTag + "[SUP]".length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(AbsoluteSizeSpan(0), endTag, endTag + "[/SUP]".length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(ForegroundColorSpan(Color.TRANSPARENT), endTag, endTag + "[/SUP]".length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            
            val contentStart = startTag + "[SUP]".length
            val contentEnd = endTag
            if (contentEnd > contentStart) {
                spannable.setSpan(SuperscriptSpan(), contentStart, contentEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(RelativeSizeSpan(0.7f), contentStart, contentEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            lastIdx = endTag + "[/SUP]".length
        }

        // 4. Format [NOTE:note_id]...[/NOTE]
        lastIdx = 0
        val noteRegex = Regex("""\[NOTE:([^\]]+)\]""")
        while (true) {
            val match = noteRegex.find(str, lastIdx) ?: break
            val startTag = match.range.first
            val startTagEnd = match.range.last + 1
            val noteId = match.groupValues[1]
            
            val endTag = str.indexOf("[/NOTE]", startTagEnd)
            if (endTag == -1) break
            
            // Hide tags
            spannable.setSpan(AbsoluteSizeSpan(0), startTag, startTagEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(ForegroundColorSpan(Color.TRANSPARENT), startTag, startTagEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(AbsoluteSizeSpan(0), endTag, endTag + "[/NOTE]".length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(ForegroundColorSpan(Color.TRANSPARENT), endTag, endTag + "[/NOTE]".length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            
            val contentStart = startTagEnd
            val contentEnd = endTag
            if (contentEnd > contentStart) {
                spannable.setSpan(SuperscriptSpan(), contentStart, contentEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(RelativeSizeSpan(0.7f), contentStart, contentEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                
                if (onNoteClick != null) {
                    spannable.setSpan(
                        object : android.text.style.ClickableSpan() {
                            override fun onClick(widget: View) {
                                onNoteClick(noteId)
                            }
                            override fun updateDrawState(ds: android.text.TextPaint) {
                                super.updateDrawState(ds)
                                ds.isUnderlineText = false
                                ds.color = Color.parseColor("#9B59B6")
                            }
                        },
                        contentStart,
                        contentEnd,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
            lastIdx = endTag + "[/NOTE]".length
        }

        // 5. Apply dynamic paragraph first-line indents
        if (context != null) {
            val indentDp = com.nightread.app.data.SettingsManager.getParagraphIndent(context)
            if (indentDp > 0) {
                val indentInPx = (indentDp * context.resources.displayMetrics.density).toInt()
                val textStr = spannable.toString()
                var start = 0
                while (start < textStr.length) {
                    val nextNewLine = textStr.indexOf('\n', start)
                    val end = if (nextNewLine == -1) textStr.length else nextNewLine + 1
                    
                    val paragraphText = textStr.substring(start, if (nextNewLine == -1) end else nextNewLine)
                    if (paragraphText.isNotBlank() && !paragraphText.contains("[CHAPTER]") && !paragraphText.contains("[/CHAPTER]")) {
                        val shouldIndent = if (start == 0 && pageStartOffset != null) {
                            val bookText = if (com.nightread.app.data.SettingsManager.isHyphenationEnabled(context)) {
                                BookCache.hyphenatedContent ?: BookCache.content
                            } else {
                                BookCache.content
                            }
                            pageStartOffset == 0 || (pageStartOffset > 0 && pageStartOffset - 1 < bookText.length && (bookText[pageStartOffset - 1] == '\n' || bookText[pageStartOffset - 1] == '\u000C'))
                        } else {
                            true
                        }

                        if (shouldIndent) {
                            spannable.setSpan(
                                LeadingMarginSpan.Standard(indentInPx, 0),
                                start,
                                end,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        }
                    }
                    start = end
                }
            }
        }
        
        return spannable
    }

    fun formatChapterSpans(context: android.content.Context?, text: CharSequence, basePaintSize: Float): CharSequence {
        return formatAllSpans(context, text, basePaintSize, null, null)
    }

    suspend fun getPageForOffset(
        context: android.content.Context? = null,
        text: CharSequence,
        targetOffset: Int,
        availableWidth: Int,
        availableHeight: Int,
        paint: TextPaint,
        lineSpacing: Float,
        alignment: String = "justify",
        isHyphenationEnabled: Boolean = false
    ): Pair<Int, CharSequence> = withContext(Dispatchers.Default) {
        if (text.isEmpty() || availableWidth <= 0 || availableHeight <= 0) return@withContext Pair(0, "Документ пуст.")
        
        val containsSoftHyphen = text.contains('\u00AD')
        Log.d(TAG, "splitText: text contains soft hyphens: $containsSoftHyphen (count: ${text.count { it == '\u00AD' }})")
        
        val formattedText = formatChapterSpans(context, text, paint.textSize)
        
        var start = targetOffset
        while (start > 0 && formattedText[start - 1] != '\n' && formattedText[start - 1] != '\u000C') {
            start--
        }
        
        val textLength = formattedText.length
        val alignmentVal = when (alignment) {
            "left" -> Layout.Alignment.ALIGN_NORMAL
            "right" -> Layout.Alignment.ALIGN_OPPOSITE
            "center" -> Layout.Alignment.ALIGN_CENTER
            else -> Layout.Alignment.ALIGN_NORMAL
        }
        
        val strategy = if (isHyphenationEnabled) android.graphics.text.LineBreaker.BREAK_STRATEGY_HIGH_QUALITY else android.graphics.text.LineBreaker.BREAK_STRATEGY_SIMPLE
        val frequency = if (isHyphenationEnabled) android.text.Layout.HYPHENATION_FREQUENCY_FULL else android.text.Layout.HYPHENATION_FREQUENCY_NONE

        val tempLayout = StaticLayout.Builder.obtain(
            formattedText, start, (start + 8000).coerceAtMost(textLength), paint, availableWidth
        )
            .setAlignment(alignmentVal)
            .setLineSpacing(0f, lineSpacing)
            .setIncludePad(false)
            .setBreakStrategy(strategy)
            .setHyphenationFrequency(frequency)
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
        
        return@withContext Pair(start, formattedText.subSequence(start, end))
    }

    suspend fun splitTextProgressive(
        context: android.content.Context? = null,
        text: CharSequence,
        availableWidth: Int,
        availableHeight: Int,
        paint: TextPaint,
        lineSpacing: Float,
        alignment: String = "justify",
        isHyphenationEnabled: Boolean = false,
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

        val containsSoftHyphen = text.contains('\u00AD')
        Log.d(TAG, "splitTextProgressive: text contains soft hyphens: $containsSoftHyphen (count: ${text.count { it == '\u00AD' }})")

        val formattedText = formatChapterSpans(context, text, paint.textSize)
        var start = 0
        val textLength = formattedText.length
        val alignmentVal = when (alignment) {
            "left" -> Layout.Alignment.ALIGN_NORMAL
            "right" -> Layout.Alignment.ALIGN_OPPOSITE
            "center" -> Layout.Alignment.ALIGN_CENTER
            else -> Layout.Alignment.ALIGN_NORMAL
        }

        val strategy = if (isHyphenationEnabled) android.graphics.text.LineBreaker.BREAK_STRATEGY_HIGH_QUALITY else android.graphics.text.LineBreaker.BREAK_STRATEGY_SIMPLE
        val frequency = if (isHyphenationEnabled) android.text.Layout.HYPHENATION_FREQUENCY_FULL else android.text.Layout.HYPHENATION_FREQUENCY_NONE

        var pagesFound = 0
        while (start < textLength) {
            if (!isActive) return@withContext
            result.offsets.add(start)

            var chunkSize = 3000
            var tempLayout: StaticLayout
            var measureEnd: Int

            while (true) {
                measureEnd = (start + chunkSize).coerceAtMost(textLength)
                tempLayout = StaticLayout.Builder.obtain(
                    formattedText, start, measureEnd, paint, availableWidth
                )
                    .setAlignment(alignmentVal)
                    .setLineSpacing(0f, lineSpacing)
                    .setIncludePad(false)
                    .setBreakStrategy(strategy)
                    .setHyphenationFrequency(frequency)
                    .apply { if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) { setJustificationMode(android.graphics.text.LineBreaker.JUSTIFICATION_MODE_INTER_WORD) } }
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

    suspend fun splitText(
        context: android.content.Context? = null,
        text: CharSequence,
        availableWidth: Int,
        availableHeight: Int,
        paint: TextPaint,
        lineSpacing: Float,
        alignment: String = "justify",
        isHyphenationEnabled: Boolean = false
    ): PageResult {
        var finalResult = PageResult()
        splitTextProgressive(context, text, availableWidth, availableHeight, paint, lineSpacing, alignment, isHyphenationEnabled) {
            if (it.isFinished) {
                finalResult = it
            }
        }
        return finalResult
    }
}
