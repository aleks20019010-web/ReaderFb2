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
        if (context != null) {
            val indentDp = com.nightread.app.data.SettingsManager.getParagraphIndent(context)
            if (indentDp > 0 && !processedText.contains("    ")) {
                val indentStr = "    "
                val regex = Regex("\\n([^\\s ])")
                processedText = regex.replace(processedText) { match ->
                    "\n" + indentStr + match.groupValues[1]
                }
                if (processedText.isNotEmpty() && !processedText[0].isWhitespace() && processedText[0] != ' ') {
                    processedText = indentStr + processedText
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
        return formatAllSpans(context, text, basePaintSize, null, null)
    }

    fun addClickableSpans(text: CharSequence, onNoteClick: (String) -> Unit): CharSequence {
        return formatAllSpans(null, text, 0f, null, onNoteClick)
    }
}
