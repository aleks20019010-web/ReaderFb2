import sys

file = 'app/src/main/java/com/nightread/app/ui/PageSplitter.kt'
content = open(file).read()

old_code = """                spannable.setSpan(
                    AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
                    startTag,
                    endTag + "[/CHAPTER]".length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )"""

new_code = """                var alignStart = startTag
                if (alignStart > 0 && spannable[alignStart - 1] == '\u000C') {
                    alignStart--
                }
                spannable.setSpan(
                    AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
                    alignStart,
                    endTag + "[/CHAPTER]".length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )"""

content = content.replace(old_code, new_code)
open(file, 'w').write(content)
