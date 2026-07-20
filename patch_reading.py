import re

with open('app/src/main/java/com/nightread/app/ui/ReaderViewModel.kt', 'r') as f:
    content = f.read()

old_code = """if (isWebView && content.isNotEmpty()) {
                        val tagRegex = Regex("<(p|title|subtitle|h1|h2|h3|h4|h5|h6)(\\s+[^>]*|\\s*)>", RegexOption.IGNORE_CASE)
                        paragraphOffsets = tagRegex.findAll(content).map { it.range.first }.toList()
                        totalParagraphCount = paragraphOffsets.size.coerceAtLeast(1)
                    } else {"""

new_code = """if (isWebView && content.isNotEmpty()) {
                        val offsets = mutableListOf<Int>()
                        var i = 0
                        val len = content.length
                        while (i < len) {
                            val nextTagStart = content.indexOf('<', i)
                            if (nextTagStart == -1) break
                            
                            i = nextTagStart + 1
                            if (i < len && content[i] != '/') {
                                var endNameIdx = i
                                while (endNameIdx < len && content[endNameIdx] != ' ' && content[endNameIdx] != '>' && content[endNameIdx] != '\t' && content[endNameIdx] != '\n' && content[endNameIdx] != '\r') {
                                    endNameIdx++
                                }
                                if (endNameIdx > i && (endNameIdx - i) <= 8) {
                                    val tagName = content.substring(i, endNameIdx).lowercase()
                                    if (tagName == "p" || tagName == "title" || tagName == "subtitle" || 
                                        tagName == "h1" || tagName == "h2" || tagName == "h3" || 
                                        tagName == "h4" || tagName == "h5" || tagName == "h6") {
                                        offsets.add(nextTagStart)
                                    }
                                }
                            }
                        }
                        paragraphOffsets = offsets
                        totalParagraphCount = paragraphOffsets.size.coerceAtLeast(1)
                    } else {"""

content = content.replace(old_code, new_code)

with open('app/src/main/java/com/nightread/app/ui/ReaderViewModel.kt', 'w') as f:
    f.write(content)

