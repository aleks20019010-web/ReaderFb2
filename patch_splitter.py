import sys

content = open('app/src/main/java/com/nightread/app/ui/PageSplitter.kt').read()

old_chunk = """            if (foundChapterBreakIdx != -1) {
                end = foundChapterBreakIdx
                result.pages.add(formattedText.subSequence(start, end))
                start = foundChapterBreakIdx + 1
            } else {
                if (end < textLength) {
                    var spaceIndex = -1
                    for (j in (end - 1) downTo (end - 100).coerceAtLeast(start)) {
                        if (formattedText[j].isWhitespace()) {
                            spaceIndex = j
                            break
                        }
                    }
                    if (spaceIndex > start) {
                        end = spaceIndex + 1
                    }
                }
                result.pages.add(formattedText.subSequence(start, end))
                start = end
            }"""

new_chunk = """            if (foundChapterBreakIdx != -1) {
                end = foundChapterBreakIdx
                result.pages.add(formattedText.subSequence(start, end))
                start = foundChapterBreakIdx + 1
            } else {
                result.pages.add(formattedText.subSequence(start, end))
                start = end
            }"""

content = content.replace(old_chunk, new_chunk)
open('app/src/main/java/com/nightread/app/ui/PageSplitter.kt', 'w').write(content)
