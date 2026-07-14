import re

with open("app/src/main/java/com/nightread/app/ui/TextFormatter.kt", "r") as f:
    tf = f.read()

# We'll completely rewrite formatAllSpans.
# Actually, since we only need formatChapterSpans and addClickableSpans, let's just make formatChapterSpans do all the replacements and use Annotation span for notes!

new_code = """
    fun formatChapterSpans(context: Context?, text: CharSequence, basePaintSize: Float): CharSequence {
        var processedText = text.toString()
        
        // 1. Remove multiple consecutive empty lines (including any spaces between them)
        processedText = processedText.replace(Regex("([ \\\\t\\\\r ]*\\\\n[ \\\\t\\\\r ]*){2,}"), "\\n")
        
        // 2. Strip any remaining spaces at the beginning of every line
        processedText = processedText.replace(Regex("\\\\n[ \\\\t\\\\r ]+"), "\\n")
        
        if (context != null) {
            val indentDp = com.nightread.app.data.SettingsManager.getParagraphIndent(context)
            if (indentDp > 0) {
                val indentStr = "    "
                // Add indent to all lines except those starting with a page break (\\u000C)
                processedText = processedText.replace(Regex("\\\\n([^\\\\n\\\\u000C])")) { match ->
                    "\\n" + indentStr + match.groupValues[1]
                }
                if (processedText.isNotEmpty() && processedText[0] != '\\u000C') {
                    processedText = indentStr + processedText.trimStart(' ', '\\t', '\\r', '\\n', ' ')
                }
            }
        }

        val spannable = SpannableStringBuilder(processedText)
        
        // Helper to replace tags and apply spans
        fun processTag(tagOpen: String, tagClose: String, spanFactory: () -> Any) {
            while (true) {
                val startTag = spannable.indexOf(tagOpen)
                if (startTag == -1) break
                spannable.replace(startTag, startTag + tagOpen.length, "")
                
                val endTag = spannable.indexOf(tagClose, startTag)
                if (endTag == -1) break
                spannable.replace(endTag, endTag + tagClose.length, "")
                
                if (endTag > startTag) {
                    spannable.setSpan(spanFactory(), startTag, endTag, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }

        // 1. Format [CHAPTER]...[/CHAPTER]
        while (true) {
            val startTag = spannable.indexOf("[CHAPTER]")
            if (startTag == -1) break
            spannable.replace(startTag, startTag + "[CHAPTER]".length, "")
            
            val endTag = spannable.indexOf("[/CHAPTER]", startTag)
            if (endTag == -1) break
            spannable.replace(endTag, endTag + "[/CHAPTER]".length, "")
            
            if (endTag > startTag) {
                spannable.setSpan(AbsoluteSizeSpan((basePaintSize * 1.5f).toInt()), startTag, endTag, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(StyleSpan(Typeface.BOLD), startTag, endTag, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                var alignStart = startTag
                if (alignStart > 0 && spannable[alignStart - 1] == '\\u000C') {
                    alignStart--
                }
                spannable.setSpan(AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), alignStart, endTag, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        // 2. Format [CITE]...[/CITE]
        processTag("[CITE]", "[/CITE]") { StyleSpan(Typeface.ITALIC) }
        
        // 3. Format [SUP]...[/SUP]
        while (true) {
            val startTag = spannable.indexOf("[SUP]")
            if (startTag == -1) break
            spannable.replace(startTag, startTag + "[SUP]".length, "")
            
            val endTag = spannable.indexOf("[/SUP]", startTag)
            if (endTag == -1) break
            spannable.replace(endTag, endTag + "[/SUP]".length, "")
            
            if (endTag > startTag) {
                spannable.setSpan(android.text.style.SuperscriptSpan(), startTag, endTag, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(android.text.style.RelativeSizeSpan(0.6f), startTag, endTag, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        // 4. Notes
        val noteRegex = Regex(\"\"\"\\[NOTE:([^\\]]+)\\]\"\"\")
        while (true) {
            val str = spannable.toString()
            val match = noteRegex.find(str) ?: break
            val noteId = match.groupValues[1]
            val startTagLen = match.range.last - match.range.first + 1
            spannable.replace(match.range.first, match.range.last + 1, "")
            
            val endTag = spannable.indexOf("[/NOTE]", match.range.first)
            if (endTag == -1) break
            spannable.replace(endTag, endTag + "[/NOTE]".length, "")
            
            if (endTag > match.range.first) {
                spannable.setSpan(android.text.style.SuperscriptSpan(), match.range.first, endTag, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(android.text.style.RelativeSizeSpan(0.7f), match.range.first, endTag, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(android.text.Annotation("note", noteId), match.range.first, endTag, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
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
"""

start_idx = tf.find("fun formatChapterSpans")
if start_idx != -1:
    end_idx = tf.rfind("}")
    tf = tf[:start_idx] + new_code.strip() + "\n}"
    with open("app/src/main/java/com/nightread/app/ui/TextFormatter.kt", "w") as f:
        f.write(tf)
    print("Rewritten TextFormatter")
else:
    print("Not found")

