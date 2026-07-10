import sys

file = 'app/src/main/java/com/nightread/app/ui/PageSplitter.kt'
content = open(file).read()

old_code = """                spannable.setSpan(
                    AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
                    titleStart,
                    titleEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )"""

new_code = """                spannable.setSpan(
                    AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
                    startTag,
                    endTag + "[/CHAPTER]".length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )"""

content = content.replace(old_code, new_code)
open(file, 'w').write(content)
