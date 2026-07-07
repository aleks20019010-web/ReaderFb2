import re

with open('app/src/main/java/com/nightread/app/ui/ReadingActivity.kt', 'r') as f:
    content = f.read()

old_func = r"""    private fun preprocessTextAndHyphenate(text: String): String {
        var processedText = text.replace(Regex("\n{2,}"), "\n")
        return com.nightread.app.service.RussianHyphenator.hyphenate(processedText)
    }"""

new_func = r"""    private fun preprocessTextAndHyphenate(text: String): String {
        var processedText = text.replace(Regex("(\\s*\\n\\s*)+"), "\n    ")
        return com.nightread.app.service.RussianHyphenator.hyphenate(processedText).trim()
    }"""

content = content.replace(old_func, new_func)

with open('app/src/main/java/com/nightread/app/ui/ReadingActivity.kt', 'w') as f:
    f.write(content)
