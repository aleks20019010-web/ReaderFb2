import re

with open("app/src/main/java/com/nightread/app/ui/TextFormatter.kt", "r") as f:
    text_formatter = f.read()

pattern = r"    fun formatAllSpans\(.*?    \): CharSequence \{"
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
            if (indentDp > 0 && !processedText.contains("\\u00A0\\u00A0\\u00A0\\u00A0")) {
                // Fallback for old cached books: add indents
                val indentStr = "\\u00A0\\u00A0\\u00A0\\u00A0"
                val regex = Regex("\\n([^\\\\s\\\\u00A0])")
                processedText = regex.replace(processedText) { match ->
                    "\\n" + indentStr + match.groupValues[1]
                }
                if (processedText.isNotEmpty() && !processedText[0].isWhitespace() && processedText[0] != '\\u00A0') {
                    processedText = indentStr + processedText
                }
            }
        }
        val finalCharSequence: CharSequence = processedText
"""

new_tf = re.sub(pattern, replacement, text_formatter, flags=re.DOTALL)
new_tf = new_tf.replace("if (text.isEmpty()) return text", "if (finalCharSequence.isEmpty()) return finalCharSequence")
new_tf = new_tf.replace("val spannable = SpannableStringBuilder(text)", "val spannable = SpannableStringBuilder(finalCharSequence)")
new_tf = new_tf.replace("val str = text.toString()", "val str = finalCharSequence.toString()")

with open("app/src/main/java/com/nightread/app/ui/TextFormatter.kt", "w") as f:
    f.write(new_tf)
print("Patched TextFormatter.kt with dynamic fallback indent")
