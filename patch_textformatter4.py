import re

with open("app/src/main/java/com/nightread/app/ui/TextFormatter.kt", "r") as f:
    tf = f.read()

pattern = r'        var processedText = text\.toString\(\).*?val finalCharSequence: CharSequence = processedText'

repl = """        var processedText = text.toString()
        
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

        val finalCharSequence: CharSequence = processedText"""

tf = re.sub(pattern, repl, tf, flags=re.DOTALL)

with open("app/src/main/java/com/nightread/app/ui/TextFormatter.kt", "w") as f:
    f.write(tf)

print("Success")
