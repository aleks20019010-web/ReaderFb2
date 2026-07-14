package com.nightread.app.ui

import android.text.Layout
import android.text.Spanned
import android.text.SpannableStringBuilder
import android.text.style.AbsoluteSizeSpan
import android.text.style.AlignmentSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.SuperscriptSpan
import android.graphics.Color
import android.graphics.Typeface
import android.view.View

object TextFormatter {
    private const val TAG = "TextFormatter"

    data class PageResult(
        var pages: MutableList<CharSequence> = mutableListOf(),
        var offsets: MutableList<Int> = mutableListOf(),
        var isFinished: Boolean = false
    )

    fun formatAllSpans(
        context: android.content.Context?,
        text: CharSequence,
        basePaintSize: Float,
        pageStartOffset: Int? = null,
        onNoteClick: ((String) -> Unit)? = null
    ): CharSequence {
        if (text.isEmpty()) return text

        val spannable = SpannableStringBuilder(text)
        val str = text.toString()
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
                val textStr = str
                var start = 0
                while (start < textStr.length) {
                    val nextNewLine = textStr.indexOf('\n', start)
                    val end = if (nextNewLine == -1) textStr.length else nextNewLine + 1
                    
                    val paraEnd = if (nextNewLine == -1) end else nextNewLine
                    var isBlank = true
                    for (i in start until paraEnd) {
                        if (!textStr[i].isWhitespace()) {
                            isBlank = false
                            break
                        }
                    }
                    
                    if (!isBlank) {
                        var hasChapter = false
                        val chapterIdx = textStr.indexOf("[CHAPTER]", start)
                        if (chapterIdx != -1 && chapterIdx < paraEnd) hasChapter = true
                        val endChapterIdx = textStr.indexOf("[/CHAPTER]", start)
                        if (endChapterIdx != -1 && endChapterIdx < paraEnd) hasChapter = true
                        
                        if (!hasChapter) {
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
                            
                            var firstNonSpace = start
                            while (firstNonSpace < paraEnd) {
                                val c = textStr[firstNonSpace]
                                if (Character.isWhitespace(c.code) || Character.isSpaceChar(c.code) || c == '\u00A0' || c == '\u200B') {
                                    firstNonSpace++
                                } else {
                                    break
                                }
                            }

                            if (shouldIndent) {
                                if (firstNonSpace > start) {
                                    spannable.setSpan(
                                        android.text.style.ScaleXSpan(0f),
                                        start,
                                        firstNonSpace,
                                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                    )
                                    spannable.setSpan(
                                        android.text.style.AbsoluteSizeSpan(0),
                                        start,
                                        firstNonSpace,
                                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                    )
                                }
                                spannable.setSpan(
                                    LeadingMarginSpan.Standard(indentInPx, 0),
                                    start,
                                    end,
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                )
                            }
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

    fun addClickableSpans(text: CharSequence, onNoteClick: (String) -> Unit): CharSequence {
        val spannable = SpannableStringBuilder(text)
        val str = text.toString()
        var lastIdx = 0
        val noteRegex = Regex("""\[NOTE:([^\]]+)\]""")
        while (true) {
            val match = noteRegex.find(str, lastIdx) ?: break
            val startTagEnd = match.range.last + 1
            val noteId = match.groupValues[1]

            val endTag = str.indexOf("[/NOTE]", startTagEnd)
            if (endTag == -1) break

            val contentStart = startTagEnd
            val contentEnd = endTag
            if (contentEnd > contentStart) {
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
            lastIdx = endTag + "[/NOTE]".length
        }
        return spannable
    }
}
