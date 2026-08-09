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
