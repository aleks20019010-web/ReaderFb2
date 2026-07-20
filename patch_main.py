import re

with open('app/src/main/java/com/nightread/app/MainActivity.kt', 'r') as f:
    content = f.read()

old_code = r"""                                    if (isWebView) {
                                        val tagRegex = Regex("<(p|title|subtitle|h1|h2|h3|h4|h5|h6)(\\s+[^>]*|\\s*)>", RegexOption.IGNORE_CASE)
                                        val offsets = tagRegex.findAll(bookContent).map { it.range.first }.toList()
                                        BookCache.paragraphOffsets = offsets
                                        BookCache.totalParagraphCount = offsets.size.coerceAtLeast(1)
                                    } else {"""

new_code = r"""                                    if (isWebView) {
                                        val offsets = mutableListOf<Int>()
                                        var i = 0
                                        val len = bookContent.length
                                        while (i < len) {
                                            val nextTagStart = bookContent.indexOf('<', i)
                                            if (nextTagStart == -1) break
                                            i = nextTagStart + 1
                                            if (i < len && bookContent[i] != '/') {
                                                var endNameIdx = i
                                                while (endNameIdx < len && bookContent[endNameIdx] != ' ' && bookContent[endNameIdx] != '>' && bookContent[endNameIdx] != '\t' && bookContent[endNameIdx] != '\n' && bookContent[endNameIdx] != '\r') {
                                                    endNameIdx++
                                                }
                                                if (endNameIdx > i && (endNameIdx - i) <= 8) {
                                                    val tagName = bookContent.substring(i, endNameIdx).lowercase()
                                                    if (tagName == "p" || tagName == "title" || tagName == "subtitle" || 
                                                        tagName == "h1" || tagName == "h2" || tagName == "h3" || 
                                                        tagName == "h4" || tagName == "h5" || tagName == "h6") {
                                                        offsets.add(nextTagStart)
                                                    }
                                                }
                                            }
                                        }
                                        BookCache.paragraphOffsets = offsets
                                        BookCache.totalParagraphCount = offsets.size.coerceAtLeast(1)
                                    } else {"""

if old_code in content:
    content = content.replace(old_code, new_code)
    with open('app/src/main/java/com/nightread/app/MainActivity.kt', 'w') as f:
        f.write(content)
    print("Patched MainActivity.kt")
else:
    print("old_code not found in MainActivity.kt")

