import sys

file = 'app/src/main/java/com/nightread/app/ui/ReadingActivity.kt'
content = open(file).read()

old_code = """        val textToSplit = if (hyphenationEnabled) {
            com.nightread.app.ui.HyphenationPatterns.load("ru")
            com.nightread.app.ui.HyphenatorHelper.hyphenate(bookContent)
        } else {
            bookContent
        }"""

new_code = """        val textToSplit = if (hyphenationEnabled) {
            if (BookCache.sha1 == this.sha1 && BookCache.isHyphenated == true && BookCache.hyphenatedContent != null) {
                BookCache.hyphenatedContent!!
            } else {
                val hyphenated = withContext(Dispatchers.Default) {
                    com.nightread.app.ui.HyphenationPatterns.load("ru")
                    com.nightread.app.ui.HyphenatorHelper.hyphenate(bookContent)
                }
                BookCache.hyphenatedContent = hyphenated
                BookCache.isHyphenated = true
                hyphenated
            }
        } else {
            BookCache.isHyphenated = false
            BookCache.hyphenatedContent = null
            bookContent
        }"""

content = content.replace(old_code, new_code)
open(file, 'w').write(content)
