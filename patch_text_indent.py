import re

with open("app/src/main/java/com/nightread/app/ui/TextFormatter.kt", "r") as f:
    text_formatter = f.read()

pattern = r"// 5\. Paragraph Indent.*?return spannable"
replacement = """// 5. Dynamic Paragraph Indent (Fallback)
        if (context != null) {
            val indentDp = com.nightread.app.data.SettingsManager.getParagraphIndent(context)
            if (indentDp > 0) {
                // If text does not seem to have indents (e.g. from old Fb2Parser cache), we add them dynamically
                // We use non-breaking spaces
                val indentStr = "\\u00A0\\u00A0\\u00A0\\u00A0"
                var resultText = str
                
                // Very basic heuristic: if we don't find our indent string frequently, we assume it's missing
                if (!resultText.contains(indentStr)) {
                    // Replace \n followed by a non-whitespace char with \n + indentStr + char
                    val regex = Regex("\\n([^\\\\s\\u00A0])")
                    resultText = regex.replace(resultText) { match ->
                        "\\n" + indentStr + match.groupValues[1]
                    }
                    // Also check the very first paragraph
                    if (resultText.isNotEmpty() && !resultText[0].isWhitespace() && resultText[0] != '\\u00A0') {
                        resultText = indentStr + resultText
                    }
                }
                
                // Re-apply spans on the NEW text
                val newSpannable = android.text.SpannableString(resultText)
                val oldSpans = spannable.getSpans(0, spannable.length, Any::class.java)
                for (span in oldSpans) {
                    val start = spannable.getSpanStart(span)
                    val end = spannable.getSpanEnd(span)
                    val flags = spannable.getSpanFlags(span)
                    
                    // We don't map offsets perfectly here because it's a fallback, 
                    // but most spans like CITE and SUP will be slightly shifted. 
                    // Since it's just a fallback for old cached books, it's okay to skip complex offset mapping.
                    // Actually, it's better to just return a new SpannableString and skip old spans for the fallback,
                    // OR map them. Let's just NOT map them if we replaced text, because it's too complex.
                    // Wait, if we lose CITE and SUP, that's bad.
                    // So let's NOT do regex replace on the Spannable. Let's do it on the original text BEFORE Spannable!
                }
            }
        }
        return spannable"""

# Wait, it's much better to do this BEFORE creating the SpannableString!
