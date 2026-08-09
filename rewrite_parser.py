with open("app/src/main/java/com/nightread/app/ui/customlayout/ReaderLayoutEngine.kt", "r") as f:
    content = f.read()

import re

# We will replace the body of `parseDocument` up to `val finalEndOffset = mainText.length`
start_marker = "suspend fun parseDocument(bookId: String, mainText: String, baseFontSize: androidx.compose.ui.unit.TextUnit): ReaderDocument = withContext(Dispatchers.Default) {"
end_marker = "val finalEndOffset = mainText.length"

if start_marker in content and end_marker in content:
    idx1 = content.find(start_marker) + len(start_marker)
    idx2 = content.find(end_marker)
    
    new_body = """
        val paragraphs = mutableListOf<ReaderParagraph>()
        val chapters = mutableListOf<ReaderChapter>()
        
        var currentGlobalOffset = 0
        var chapterStartOffset = 0
        var currentChapterParas = mutableListOf<ReaderParagraph>()
        var currentChapterTitle = "Начало книги"
        var chapterIndex = 0
        
        val length = mainText.length
        
        fun stripTags(input: String): String {
            val sb = StringBuilder()
            var inTag = false
            for (i in 0 until input.length) {
                val c = input[i]
                if (c == '<') {
                    inTag = true
                } else if (c == '>') {
                    if (inTag) inTag = false
                    else sb.append(c)
                } else {
                    if (!inTag) sb.append(c)
                }
            }
            return sb.toString()
        }
        
        while (currentGlobalOffset <= length) {
            val nextNewline = mainText.indexOf('\\n', currentGlobalOffset)
            val isLastLine = nextNewline == -1
            val lineEnd = if (isLastLine) length else nextNewline
            
            val lineStart = currentGlobalOffset
            val lineLength = lineEnd - lineStart
            val line = mainText.substring(lineStart, lineEnd)
            
            currentGlobalOffset = lineEnd + if (isLastLine) 0 else 1
            
            var isBlank = true
            for (i in 0 until lineLength) {
                if (!line[i].isWhitespace()) {
                    isBlank = false
                    break
                }
            }
            if (isBlank) continue
            
            val trimmed = line.trim()
            
            var isChapterTitle = false
            if (trimmed.startsWith("<h1") || trimmed.startsWith("<title") || trimmed.startsWith("[CHAPTER]")) {
                isChapterTitle = true
            } else if (trimmed.startsWith("<h1>") && trimmed.endsWith("</h1>")) {
                isChapterTitle = true
            } else if (trimmed.length < 80) {
                if (trimmed.startsWith("Глава") || trimmed.startsWith("Chapter")) {
                    isChapterTitle = true
                } else {
                    val dotIdx = trimmed.indexOf('.')
                    if (dotIdx > 0) {
                        var isRomanOrNum = true
                        for (i in 0 until dotIdx) {
                            val c = trimmed[i]
                            if (!(c in '0'..'9' || c == 'I' || c == 'V' || c == 'X' || c == 'L' || c == 'C' || c == 'D' || c == 'M')) {
                                isRomanOrNum = false
                                break
                            }
                        }
                        if (isRomanOrNum) isChapterTitle = true
                    }
                }
            }
            
            if (isChapterTitle && currentChapterParas.isNotEmpty()) {
                val endOffset = lineStart
                val chunks = buildChunksForChapter(chapterIndex, currentChapterParas, chapterStartOffset, endOffset)
                chapters.add(
                    ReaderChapter(
                        chapterIndex = chapterIndex,
                        title = currentChapterTitle,
                        startOffset = chapterStartOffset,
                        endOffset = endOffset,
                        paragraphs = currentChapterParas,
                        chunks = chunks
                    )
                )
                paragraphs.addAll(currentChapterParas)
                chapterIndex++
                currentChapterParas = mutableListOf()
                chapterStartOffset = lineStart
                currentChapterTitle = stripTags(line).trim().take(60)
                if (currentChapterTitle.isBlank()) currentChapterTitle = "Глава ${chapterIndex + 1}"
            }
            
            val inlines = if (isChapterTitle) {
                val cleanLine = stripTags(line)
                listOf(
                    ReaderInline.Styled(
                        content = cleanLine,
                        style = androidx.compose.ui.text.SpanStyle(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            fontSize = baseFontSize * 1.5f
                        ),
                        globalStartOffset = lineStart,
                        globalEndOffset = lineStart + cleanLine.length
                    )
                )
            } else {
                parseInlines(line, lineStart, lineEnd, baseFontSize)
            }
            
            val actualLine = if (isChapterTitle) stripTags(line) else line
            currentChapterParas.add(
                ReaderParagraph(
                    rawText = actualLine,
                    inlines = inlines,
                    globalStartOffset = lineStart,
                    globalEndOffset = lineStart + actualLine.length
                )
            )
            
            if (isChapterTitle && currentChapterParas.size == 1) {
                currentChapterTitle = actualLine.trim().take(60)
                if (currentChapterTitle.isBlank()) currentChapterTitle = "Глава ${chapterIndex + 1}"
            }
        }
        
        """
    
    new_file = content[:idx1] + new_body + content[idx2:]
    with open("app/src/main/java/com/nightread/app/ui/customlayout/ReaderLayoutEngine.kt", "w") as f:
        f.write(new_file)
    print("Patched successfully")
else:
    print("Markers not found!")
