import re

with open("app/src/main/java/com/nightread/app/ui/TextFormatter.kt", "r") as f:
    text_formatter = f.read()

target = """    fun formatAllSpans(
        context: Context?,
        text: CharSequence,
        basePaintSize: Float,
        pageStartOffset: Int? = null,
        onNoteClick: ((String) -> Unit)? = null
    ): CharSequence {"""

replacement = """    fun formatAllSpans(
        context: Context?,
        text: CharSequence,
        basePaintSize: Float,
        pageStartOffset: Int? = null,
        onNoteClick: ((String) -> Unit)? = null
    ): CharSequence {
        var processedText = text.toString()
        if (context != null) {
            val indentDp = com.nightread.app.data.SettingsManager.getParagraphIndent(context)
            if (indentDp > 0 && !processedText.contains("\u00A0\u00A0\u00A0\u00A0")) {
                val indentStr = "\u00A0\u00A0\u00A0\u00A0"
                val regex = Regex("\\n([^\\s\u00A0])")
                processedText = regex.replace(processedText) { match ->
                    "\\n" + indentStr + match.groupValues[1]
                }
                if (processedText.isNotEmpty() && !processedText[0].isWhitespace() && processedText[0] != '\u00A0') {
                    processedText = indentStr + processedText
                }
            }
        }
        val finalCharSequence: CharSequence = processedText"""

text_formatter = text_formatter.replace(target, replacement)
text_formatter = text_formatter.replace("if (text.isEmpty()) return text", "if (finalCharSequence.isEmpty()) return finalCharSequence")
text_formatter = text_formatter.replace("val spannable = SpannableStringBuilder(text)", "val spannable = SpannableStringBuilder(finalCharSequence)")
text_formatter = text_formatter.replace("val str = text.toString()", "val str = finalCharSequence.toString()")

with open("app/src/main/java/com/nightread/app/ui/TextFormatter.kt", "w") as f:
    f.write(text_formatter)
print("Patched TextFormatter.kt with dynamic fallback indent")
