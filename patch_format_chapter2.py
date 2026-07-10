import sys

file = 'app/src/main/java/com/nightread/app/ui/PageSplitter.kt'
content = open(file).read()

old_code2 = r"""        val containsSoftHyphen = text.contains('\u00AD')
        Log.d(TAG, "splitTextProgressive: text contains soft hyphens: $containsSoftHyphen (count: ${text.count { it == '\u00AD' }})")
        val formattedText = text"""

new_code2 = r"""        val containsSoftHyphen = text.contains('\u00AD')
        Log.d(TAG, "splitTextProgressive: text contains soft hyphens: $containsSoftHyphen (count: ${text.count { it == '\u00AD' }})")
        val formattedText = formatChapterSpans(text, paint.textSize)"""

content = content.replace(old_code2, new_code2)

open(file, 'w').write(content)
