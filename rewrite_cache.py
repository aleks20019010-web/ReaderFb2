import re
with open("app/src/main/java/com/nightread/app/ui/PaginationDiskCache.kt", "r") as f:
    content = f.read()

# We will completely replace the file, so just generate the new content.
new_content = """package com.nightread.app.ui

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

object PaginationDiskCache {
    private const val MAGIC = 0x50414743 // "PAGC"
    private const val CACHE_FORMAT_VERSION = 2

    private fun getCacheDir(context: Context): File {
        val dir = File(context.cacheDir, "pagination_chapters")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getChunkCacheFile(context: Context, sha1: String, layoutKey: String, chapterIndex: Int, chunkIndex: Int): File {
        val digest = MessageDigest.getInstance("MD5")
        val hashBytes = digest.digest((sha1 + "_" + layoutKey + "_ch_" + chapterIndex + "_chunk_" + chunkIndex).toByteArray(Charsets.UTF_8))
        val hashString = hashBytes.joinToString("") { "%02x".format(it) }
        return File(getCacheDir(context), "$hashString.bin")
    }

    suspend fun getChapterChunkPages(
        context: Context, 
        sha1: String, 
        layoutKey: String, 
        chapterIndex: Int, 
        chunkIndex: Int,
        chunk: com.nightread.app.ui.customlayout.ReaderChunk,
        baseFontSize: androidx.compose.ui.unit.TextUnit
    ): List<com.nightread.app.ui.customlayout.ReaderPage>? = withContext(Dispatchers.IO) {
        val file = getChunkCacheFile(context, sha1, layoutKey, chapterIndex, chunkIndex)
        if (!file.exists()) return@withContext null

        try {
            val pages = mutableListOf<com.nightread.app.ui.customlayout.ReaderPage>()
            DataInputStream(FileInputStream(file)).use { dis ->
                val magic = dis.readInt()
                if (magic != MAGIC) return@withContext null
                val version = dis.readInt()
                if (version != CACHE_FORMAT_VERSION) return@withContext null

                val fSha1 = dis.readUTF()
                if (fSha1 != sha1) return@withContext null
                val fLayoutKey = dis.readUTF()
                if (fLayoutKey != layoutKey) return@withContext null
                val fChapterIndex = dis.readInt()
                if (fChapterIndex != chapterIndex) return@withContext null
                val fChunkIndex = dis.readInt()
                if (fChunkIndex != chunkIndex) return@withContext null

                val size = dis.readInt()
                if (size < 0) return@withContext null
                
                // Read bounds
                val bounds = mutableListOf<Bounds>()
                for (i in 0 until size) {
                    val pageIdx = dis.readInt()
                    val startSrc = dis.readInt()
                    val endSrc = dis.readInt()
                    val startDisp = dis.readInt()
                    val endDisp = dis.readInt()
                    bounds.add(Bounds(pageIdx, startSrc, endSrc, startDisp, endDisp))
                }
                
                if (bounds.isEmpty()) return@withContext null

                // Reconstruct AnnotatedString
                val mappingResult = com.nightread.app.ui.customlayout.ReaderLayoutEngine.buildAnnotatedStringForChunk(chunk, baseFontSize)
                val annotated = mappingResult.annotatedString
                
                for (b in bounds) {
                    val slice = if (b.startDisp < b.endDisp && b.endDisp <= annotated.length) {
                        com.nightread.app.ui.customlayout.ReaderLayoutEngine.trimTrailingWhitespace(annotated.subSequence(b.startDisp, b.endDisp))
                    } else {
                        androidx.compose.ui.text.AnnotatedString("")
                    }
                    pages.add(
                        com.nightread.app.ui.customlayout.ReaderPage(
                            pageIndex = b.pageIndex,
                            text = slice,
                            startOffset = b.startSrc,
                            endOffset = b.endSrc,
                            startDisplayOffset = b.startDisp,
                            endDisplayOffset = b.endDisp
                        )
                    )
                }
            }
            if (pages.isNotEmpty()) pages else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private data class Bounds(
        val pageIndex: Int,
        val startSrc: Int,
        val endSrc: Int,
        val startDisp: Int,
        val endDisp: Int
    )

    suspend fun saveChapterChunkPages(
        context: Context, 
        sha1: String, 
        layoutKey: String, 
        chapterIndex: Int, 
        chunkIndex: Int, 
        pages: List<com.nightread.app.ui.customlayout.ReaderPage>
    ) = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        val file = getChunkCacheFile(context, sha1, layoutKey, chapterIndex, chunkIndex)
        try {
            val tempFile = File(file.parent, file.name + ".tmp")
            DataOutputStream(FileOutputStream(tempFile)).use { dos ->
                dos.writeInt(MAGIC)
                dos.writeInt(CACHE_FORMAT_VERSION)
                dos.writeUTF(sha1)
                dos.writeUTF(layoutKey)
                dos.writeInt(chapterIndex)
                dos.writeInt(chunkIndex)
                dos.writeInt(pages.size)
                
                for (page in pages) {
                    dos.writeInt(page.pageIndex)
                    dos.writeInt(page.startOffset)
                    dos.writeInt(page.endOffset)
                    dos.writeInt(page.startDisplayOffset)
                    dos.writeInt(page.endDisplayOffset)
                }
            }
            tempFile.renameTo(file)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        com.nightread.app.ui.customlayout.ReaderMetrics.logCacheWrite(System.currentTimeMillis() - startMs)
    }

    // Legacy support (still needed by ReaderViewModel for Chapter offsets if any? wait, getOffsets/saveOffsets are used for something else?
    // Let's keep them, but using versioned binary format isn't strictly requested for them, though we can.)
    suspend fun getOffsets(context: Context, sha1: String, layoutKey: String): List<Int>? = withContext(Dispatchers.IO) {
        val file = File(getCacheDir(context), "${sha1}_${layoutKey}_legacy.bin")
        if (!file.exists()) return@withContext null
        try {
            val offsets = mutableListOf<Int>()
            DataInputStream(FileInputStream(file)).use { dis ->
                val size = dis.readInt()
                for (i in 0 until size) {
                    offsets.add(dis.readInt())
                }
            }
            if (offsets.isNotEmpty()) offsets else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveOffsets(context: Context, sha1: String, layoutKey: String, offsets: List<Int>) = withContext(Dispatchers.IO) {
        try {
            val file = File(getCacheDir(context), "${sha1}_${layoutKey}_legacy.bin")
            val tempFile = File(file.parent, file.name + ".tmp")
            DataOutputStream(FileOutputStream(tempFile)).use { dos ->
                dos.writeInt(offsets.size)
                for (offset in offsets) {
                    dos.writeInt(offset)
                }
            }
            tempFile.renameTo(file)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
"""

with open("app/src/main/java/com/nightread/app/ui/PaginationDiskCache.kt", "w") as f:
    f.write(new_content)
