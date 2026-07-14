with open("app/src/main/java/com/nightread/app/ui/TextFormatter.kt", "r") as f:
    tf = f.read()

import re

target = """        var processedText = text.toString()
        if (context != null) {
            val indentDp = com.nightread.app.data.SettingsManager.getParagraphIndent(context)
            if (indentDp > 0 && !processedText.contains("    ")) {
                val indentStr = "    "
                val regex = Regex("\\n([^\\\\s ])")
                processedText = regex.replace(processedText) { match ->
                    "\\n" + indentStr + match.groupValues[1]
                }
                if (processedText.isNotEmpty() && !processedText[0].isWhitespace() && processedText[0] != ' ') {
                    processedText = indentStr + processedText
                }
            }
        }"""

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
        }"""

if target in tf:
    tf = tf.replace(target, repl)
    with open("app/src/main/java/com/nightread/app/ui/TextFormatter.kt", "w") as f:
        f.write(tf)
    print("Success")
else:
    print("Not found")
