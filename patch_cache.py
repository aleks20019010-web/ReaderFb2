with open("app/src/main/java/com/nightread/app/ui/PaginationDiskCache.kt", "r") as f:
    content = f.read()

replacement = """
    suspend fun saveChapterChunkPages(context: Context, sha1: String, layoutKey: String, chapterIndex: Int, chunkIndex: Int, pages: List<com.nightread.app.ui.customlayout.ReaderPage>) = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        val file = getChunkCacheFile(context, sha1, layoutKey, chapterIndex, chunkIndex)
        try {
            val tempFile = File(file.parent, file.name + ".tmp")
            DataOutputStream(FileOutputStream(tempFile)).use { dos ->
                dos.writeInt(pages.size)
                for (page in pages) {
                    dos.writeInt(page.pageIndex)
                    dos.writeInt(page.startOffset)
                    dos.writeInt(page.endOffset)
                    val textBytes = page.text.text.toByteArray(Charsets.UTF_8)
                    dos.writeInt(textBytes.size)
                    dos.write(textBytes)
                }
            }
            tempFile.renameTo(file)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        com.nightread.app.ui.customlayout.ReaderMetrics.logCacheWrite(System.currentTimeMillis() - startMs)
    }
"""

import re
content = re.sub(r'suspend fun saveChapterChunkPages.*?catch \(e: Exception\) \{\s*e\.printStackTrace\(\)\s*\}\s*\}', replacement.strip(), content, flags=re.DOTALL)

with open("app/src/main/java/com/nightread/app/ui/PaginationDiskCache.kt", "w") as f:
    f.write(content)
