import re

with open("app/src/main/java/com/nightread/app/ui/TextFormatter.kt", "r") as f:
    code = f.read()

pattern = r"        if \(context != null\) \{.*        return spannable"

replacement = """        if (context != null) {
            val indentDp = com.nightread.app.data.SettingsManager.getParagraphIndent(context)
            if (indentDp > 0) {
                val indentInPx = (indentDp * context.resources.displayMetrics.density).toInt()
                val textStr = str
                var start = 0
                while (start < textStr.length) {
                    val nextNewLine = textStr.indexOf('\\n', start)
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
                                pageStartOffset == 0 || (pageStartOffset > 0 && pageStartOffset - 1 < bookText.length && (bookText[pageStartOffset - 1] == '\\n' || bookText[pageStartOffset - 1] == '\\u000C'))
                            } else {
                                true
                            }
                            
                            val firstChar = textStr[start]
                            val startsWithWhitespace = firstChar.isWhitespace() || firstChar == '\\u00A0' || firstChar == '\\t'
                            if (shouldIndent && !startsWithWhitespace) {
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
        
        return spannable"""

new_code = re.sub(pattern, replacement, code, flags=re.DOTALL)
if code == new_code:
    print("Failed!")
else:
    with open("app/src/main/java/com/nightread/app/ui/TextFormatter.kt", "w") as f:
        f.write(new_code)
    print("Success!")
