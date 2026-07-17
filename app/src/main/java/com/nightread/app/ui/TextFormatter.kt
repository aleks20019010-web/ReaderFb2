package com.nightread.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.AlignmentSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.ScaleXSpan
import android.text.style.StyleSpan
import com.nightread.app.data.SettingsManager

object TextFormatter {

    private class ZeroWidthSpan : android.text.style.ReplacementSpan() {
        override fun getSize(paint: android.graphics.Paint, text: CharSequence, start: Int, end: Int, fm: android.graphics.Paint.FontMetricsInt?): Int = 0
        override fun draw(canvas: android.graphics.Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: android.graphics.Paint) {}
    }


    data class PageResult(
        var pages: MutableList<CharSequence> = mutableListOf(),
        var offsets: MutableList<Int> = mutableListOf(),
        var isFinished: Boolean = false
    )

    fun formatAllSpans(
        context: Context?,
        text: CharSequence,
        basePaintSize: Float,
        pageStartOffset: Int? = null,
        onNoteClick: ((String) -> Unit)? = null
    ): CharSequence {
        var processedText = text.toString()
        
        // 1. Remove multiple consecutive empty lines (including any spaces between them)
        processedText = processedText.replace(Regex("([ \\t\\r ]*\\n[ \\t\\r ]*){2,}"), "\n")
        
        // 2. Strip any remaining spaces at the beginning of every line
        processedText = processedText.replace(Regex("\\n[ \\t\\r ]+"), "\n")
        
        if (context != null) {
            val indentDp = com.nightread.app.data.SettingsManager.getParagraphIndent(context)
            if (indentDp > 0) {
                val indentStr = "    "
                // Add indent to all lines except those starting with a page break (\u000C)
                processedText = processedText.replace(Regex("\\n([^\\n\\u000C])")) { match ->
                    "\n" + indentStr + match.groupValues[1]
                }
                if (processedText.isNotEmpty() && processedText[0] != '\u000C') {
                    processedText = indentStr + processedText.trimStart(' ', '\t', '\r', '\n', ' ')
                }
            }
        }

        val finalCharSequence: CharSequence = processedText
        if (finalCharSequence.isEmpty()) return finalCharSequence

        val spannable = SpannableStringBuilder(finalCharSequence)
        val str = finalCharSequence.toString()
        var lastIdx = 0

        // 1. Format [CHAPTER]...[/CHAPTER]
        while (true) {
            val startTag = str.indexOf("[CHAPTER]", lastIdx)
            if (startTag == -1) break
            val endTag = str.indexOf("[/CHAPTER]", startTag)
            if (endTag == -1) break
            
            // Hide tags
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

            spannable.setSpan(AbsoluteSizeSpan(0), startTag, startTag + "[CITE]".length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(ForegroundColorSpan(Color.TRANSPARENT), startTag, startTag + "[CITE]".length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            
            spannable.setSpan(AbsoluteSizeSpan(0), endTag, endTag + "[/CITE]".length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(ForegroundColorSpan(Color.TRANSPARENT), endTag, endTag + "[/CITE]".length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            val citeStart = startTag + "[CITE]".length
            val citeEnd = endTag
            if (citeEnd > citeStart) {
                spannable.setSpan(StyleSpan(Typeface.ITALIC), citeStart, citeEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
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

            spannable.setSpan(AbsoluteSizeSpan(0), startTag, startTag + "[SUP]".length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(ForegroundColorSpan(Color.TRANSPARENT), startTag, startTag + "[SUP]".length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            
            spannable.setSpan(AbsoluteSizeSpan(0), endTag, endTag + "[/SUP]".length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(ForegroundColorSpan(Color.TRANSPARENT), endTag, endTag + "[/SUP]".length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            val supStart = startTag + "[SUP]".length
            val supEnd = endTag
            if (supEnd > supStart) {
                spannable.setSpan(android.text.style.SuperscriptSpan(), supStart, supEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(android.text.style.RelativeSizeSpan(0.6f), supStart, supEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            lastIdx = endTag + "[/SUP]".length
        }

        // 4. Notes
        if (onNoteClick != null) {
            val noteRegex = Regex("""\[NOTE:([^\]]+)\]""")
            lastIdx = 0
            while (true) {
                val match = noteRegex.find(str, lastIdx) ?: break
                val startTagEnd = match.range.last + 1
                val noteId = match.groupValues[1]

                val endTag = str.indexOf("[/NOTE]", startTagEnd)
                if (endTag == -1) break

                val contentStart = startTagEnd
                val contentEnd = endTag
                
                spannable.setSpan(AbsoluteSizeSpan(0), match.range.first, startTagEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(ForegroundColorSpan(Color.TRANSPARENT), match.range.first, startTagEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                
                spannable.setSpan(AbsoluteSizeSpan(0), endTag, endTag + "[/NOTE]".length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(ForegroundColorSpan(Color.TRANSPARENT), endTag, endTag + "[/NOTE]".length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

                if (contentEnd > contentStart) {
                    spannable.setSpan(
                        android.text.style.SuperscriptSpan(),
                        contentStart,
                        contentEnd,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    spannable.setSpan(
                        android.text.style.RelativeSizeSpan(0.7f),
                        contentStart,
                        contentEnd,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    spannable.setSpan(
                        object : android.text.style.ClickableSpan() {
                            override fun onClick(widget: android.view.View) {
                                onNoteClick(noteId)
                            }
                            override fun updateDrawState(ds: android.text.TextPaint) {
                                super.updateDrawState(ds)
                                ds.isUnderlineText = false
                                if (context != null) {
                                    ds.color = context.getColor(com.nightread.app.R.color.accent)
                                }
                            }
                        },
                        contentStart,
                        contentEnd,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                lastIdx = endTag + "[/NOTE]".length
            }
        } else {
            // Hide tags
            val noteRegex = Regex("""\[NOTE:([^\]]+)\]""")
            lastIdx = 0
            while (true) {
                val match = noteRegex.find(str, lastIdx) ?: break
                val startTagEnd = match.range.last + 1
                
                val endTag = str.indexOf("[/NOTE]", startTagEnd)
                if (endTag == -1) break
                
                spannable.setSpan(AbsoluteSizeSpan(0), match.range.first, startTagEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(ForegroundColorSpan(Color.TRANSPARENT), match.range.first, startTagEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                
                spannable.setSpan(AbsoluteSizeSpan(0), endTag, endTag + "[/NOTE]".length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(ForegroundColorSpan(Color.TRANSPARENT), endTag, endTag + "[/NOTE]".length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                
                lastIdx = endTag + "[/NOTE]".length
            }
        }

        return spannable
    }

    fun formatChapterSpans(context: Context?, text: CharSequence, basePaintSize: Float): CharSequence {
        val lines = text.toString().split('\n')
        val sb = java.lang.StringBuilder(text.length)
        var lastWasEmpty = false
        val indentDp = if (context != null) com.nightread.app.data.SettingsManager.getParagraphIndent(context) else 0
        val indentStr = "    "
        
        for (i in lines.indices) {
            val line = lines[i]
            val trimmedLine = line.trimStart(' ', '\t', '\r', ' ')
            if (trimmedLine.isEmpty()) {
                if (!lastWasEmpty) {
                    lastWasEmpty = true
                }
            } else {
                if (sb.isNotEmpty()) {
                    sb.append('\n')
                }
                lastWasEmpty = false
                
                if (indentDp > 0 && !trimmedLine.startsWith('\u000C') && !trimmedLine.contains("[CHAPTER]")) {
                    sb.append(indentStr)
                }
                sb.append(trimmedLine)
            }
        }
        val processedText = sb.toString()

        val spannable = SpannableStringBuilder()
        val str = processedText
        val len = str.length
        var i = 0
        
        while (i < len) {
            var nextTagIdx = -1
            var tagType = "" // "CHAPTER", "CITE", "SUP", "NOTE"
            var tagLength = 0
            var noteId = ""
            
            val idxChapter = str.indexOf("[CHAPTER]", i)
            val idxCite = str.indexOf("[CITE]", i)
            val idxSup = str.indexOf("[SUP]", i)
            val idxNote = str.indexOf("[NOTE:", i)
            
            var minIdx = len
            if (idxChapter != -1 && idxChapter < minIdx) { minIdx = idxChapter; tagType = "CHAPTER"; tagLength = "[CHAPTER]".length }
            if (idxCite != -1 && idxCite < minIdx) { minIdx = idxCite; tagType = "CITE"; tagLength = "[CITE]".length }
            if (idxSup != -1 && idxSup < minIdx) { minIdx = idxSup; tagType = "SUP"; tagLength = "[SUP]".length }
            if (idxNote != -1 && idxNote < minIdx) {
                val endBracket = str.indexOf("]", idxNote + "[NOTE:".length)
                if (endBracket != -1) {
                    minIdx = idxNote
                    tagType = "NOTE"
                    tagLength = endBracket - idxNote + 1
                    noteId = str.substring(idxNote + "[NOTE:".length, endBracket)
                }
            }
            
            if (minIdx == len) {
                spannable.append(str.subSequence(i, len))
                break
            }
            
            if (minIdx > i) {
                spannable.append(str.subSequence(i, minIdx))
            }
            
            val closeTag = when (tagType) {
                "CHAPTER" -> "[/CHAPTER]"
                "CITE" -> "[/CITE]"
                "SUP" -> "[/SUP]"
                "NOTE" -> "[/NOTE]"
                else -> ""
            }
            
            val closeIdx = str.indexOf(closeTag, minIdx + tagLength)
            if (closeIdx == -1) {
                spannable.append(str.subSequence(minIdx, minIdx + tagLength))
                i = minIdx + tagLength
            } else {
                val contentStart = spannable.length
                val contentText = str.subSequence(minIdx + tagLength, closeIdx)
                
                if (tagType == "CHAPTER") {
                    if (contentStart > 0 && spannable[contentStart - 1] != '\u000C') {
                        val ffIdx = spannable.length
                        spannable.append("\u000C")
                        spannable.setSpan(AbsoluteSizeSpan(0), ffIdx, ffIdx + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
                
                val actualContentStart = spannable.length
                spannable.append(contentText)
                val contentEnd = spannable.length
                
                if (contentEnd > actualContentStart) {
                    when (tagType) {
                        "CHAPTER" -> {
                            spannable.setSpan(AbsoluteSizeSpan((basePaintSize * 1.5f).toInt()), actualContentStart, contentEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            spannable.setSpan(StyleSpan(Typeface.BOLD), actualContentStart, contentEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            var alignStart = actualContentStart
                            if (alignStart > 0 && spannable[alignStart - 1] == '\u000C') {
                                alignStart--
                            }
                            spannable.setSpan(AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), alignStart, contentEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                        "CITE" -> {
                            spannable.setSpan(StyleSpan(Typeface.ITALIC), actualContentStart, contentEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                        "SUP" -> {
                            spannable.setSpan(android.text.style.SuperscriptSpan(), actualContentStart, contentEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            spannable.setSpan(android.text.style.RelativeSizeSpan(0.6f), actualContentStart, contentEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                        "NOTE" -> {
                            spannable.setSpan(android.text.style.SuperscriptSpan(), actualContentStart, contentEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            spannable.setSpan(android.text.style.RelativeSizeSpan(0.7f), actualContentStart, contentEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            spannable.setSpan(android.text.Annotation("note", noteId), actualContentStart, contentEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    }
                }
                i = closeIdx + closeTag.length
            }
        }

        return spannable
    }

    fun addClickableSpans(text: CharSequence, onNoteClick: (String) -> Unit): CharSequence {
        val spannable = SpannableStringBuilder(text)
        val annotations = spannable.getSpans(0, spannable.length, android.text.Annotation::class.java)
        for (ann in annotations) {
            if (ann.key == "note") {
                val start = spannable.getSpanStart(ann)
                val end = spannable.getSpanEnd(ann)
                val noteId = ann.value
                spannable.setSpan(
                    object : android.text.style.ClickableSpan() {
                        override fun onClick(widget: android.view.View) {
                            onNoteClick(noteId)
                        }
                        override fun updateDrawState(ds: android.text.TextPaint) {
                            super.updateDrawState(ds)
                            ds.isUnderlineText = false
                            // Assuming context is not available here, but ReadingActivity sets accent color on links anyway if needed
                        }
                    },
                    start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                // Color is applied by the TextView's linkTextColor usually
            }
        }
        return spannable
    }
}