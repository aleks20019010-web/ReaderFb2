import sys

file = 'app/src/main/java/com/nightread/app/ui/PageSplitter.kt'
content = open(file).read()

old_code = r"""        Log.d(TAG, "splitTextProgressive: text contains soft hyphens: $containsSoftHyphen (count: ${text.count { it == '\u00AD' }})")

        val formattedText = text"""

new_code = r"""        Log.d(TAG, "splitTextProgressive: text contains soft hyphens: $containsSoftHyphen (count: ${text.count { it == '\u00AD' }})")

        val formattedText = formatChapterSpans(text, paint.textSize)"""

content = content.replace(old_code, new_code)

open(file, 'w').write(content)
