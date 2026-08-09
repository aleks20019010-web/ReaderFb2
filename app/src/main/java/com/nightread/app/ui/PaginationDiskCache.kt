package com.nightread.app.ui

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
    private fun getCacheDir(context: Context): File {
        val dir = File(context.cacheDir, "pagination_chapters")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getCacheFile(context: Context, sha1: String, layoutKey: String, chapterIndex: Int): File {
        val digest = MessageDigest.getInstance("MD5")
        val hashBytes = digest.digest((sha1 + "_" + layoutKey + "_ch_" + chapterIndex).toByteArray(Charsets.UTF_8))
        val hashString = hashBytes.joinToString("") { "%02x".format(it) }
        return File(getCacheDir(context), "$hashString.bin")
    }

    suspend fun getChapterOffsets(context: Context, sha1: String, layoutKey: String, chapterIndex: Int): List<Pair<Int, Int>>? = withContext(Dispatchers.IO) {
        val file = getCacheFile(context, sha1, layoutKey, chapterIndex)
        if (!file.exists()) return@withContext null
        try {
            val pairs = mutableListOf<Pair<Int, Int>>()
            DataInputStream(FileInputStream(file)).use { dis ->
                val size = dis.readInt()
                for (i in 0 until size) {
                    val start = dis.readInt()
                    val end = dis.readInt()
                    pairs.add(start to end)
                }
            }
            if (pairs.isNotEmpty()) pairs else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun saveChapterOffsets(context: Context, sha1: String, layoutKey: String, chapterIndex: Int, pairs: List<Pair<Int, Int>>) = withContext(Dispatchers.IO) {
        val file = getCacheFile(context, sha1, layoutKey, chapterIndex)
        try {
            val tempFile = File(file.parent, file.name + ".tmp")
            DataOutputStream(FileOutputStream(tempFile)).use { dos ->
                dos.writeInt(pairs.size)
                for ((start, end) in pairs) {
                    dos.writeInt(start)
                    dos.writeInt(end)
                }
            }
            tempFile.renameTo(file)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getChunkCacheFile(context: Context, sha1: String, layoutKey: String, chapterIndex: Int, chunkIndex: Int): File {
        val digest = MessageDigest.getInstance("MD5")
        val hashBytes = digest.digest((sha1 + "_" + layoutKey + "_ch_" + chapterIndex + "_chunk_" + chunkIndex).toByteArray(Charsets.UTF_8))
        val hashString = hashBytes.joinToString("") { "%02x".format(it) }
        return File(getCacheDir(context), "$hashString.bin")
    }

    suspend fun getChapterChunkPages(context: Context, sha1: String, layoutKey: String, chapterIndex: Int, chunkIndex: Int): List<com.nightread.app.ui.customlayout.ReaderPage>? = withContext(Dispatchers.IO) {
        val file = getChunkCacheFile(context, sha1, layoutKey, chapterIndex, chunkIndex)
        if (!file.exists()) return@withContext null
        try {
            val pages = mutableListOf<com.nightread.app.ui.customlayout.ReaderPage>()
            DataInputStream(FileInputStream(file)).use { dis ->
                val size = dis.readInt()
                for (i in 0 until size) {
                    val pageIdx = dis.readInt()
                    val start = dis.readInt()
                    val end = dis.readInt()
                    val textLen = dis.readInt()
                    val textBytes = ByteArray(textLen)
                    dis.readFully(textBytes)
                    val textStr = String(textBytes, Charsets.UTF_8)
                    pages.add(
                        com.nightread.app.ui.customlayout.ReaderPage(
                            pageIndex = pageIdx,
                            text = androidx.compose.ui.text.AnnotatedString(textStr),
                            startOffset = start,
                            endOffset = end
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

    suspend fun saveChapterChunkPages(context: Context, sha1: String, layoutKey: String, chapterIndex: Int, chunkIndex: Int, pages: List<com.nightread.app.ui.customlayout.ReaderPage>) = withContext(Dispatchers.IO) {
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
    }

    // Legacy support
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
