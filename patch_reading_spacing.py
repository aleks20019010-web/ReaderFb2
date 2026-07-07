import re

with open('app/src/main/java/com/nightread/app/ui/ReadingActivity.kt', 'r') as f:
    content = f.read()

content = content.replace(
"""    private fun preprocessTextAndHyphenate(text: String): String {
        return com.nightread.app.service.RussianHyphenator.hyphenate(text)
    }""",
"""    private fun preprocessTextAndHyphenate(text: String): String {
        var processedText = text.replace(Regex("\\n{2,}"), "\\n")
        return com.nightread.app.service.RussianHyphenator.hyphenate(processedText)
    }""")

with open('app/src/main/java/com/nightread/app/ui/ReadingActivity.kt', 'w') as f:
    f.write(content)
