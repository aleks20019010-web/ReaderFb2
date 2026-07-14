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
        val dir = File(context.cacheDir, "pagination_offsets")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getCacheFile(context: Context, sha1: String, layoutKey: String): File {
        val digest = MessageDigest.getInstance("MD5")
        val hashBytes = digest.digest((sha1 + "_" + layoutKey).toByteArray(Charsets.UTF_8))
        val hashString = hashBytes.joinToString("") { "%02x".format(it) }
        return File(getCacheDir(context), "$hashString.bin")
    }

    suspend fun getOffsets(context: Context, sha1: String, layoutKey: String): List<Int>? = withContext(Dispatchers.IO) {
        val file = getCacheFile(context, sha1, layoutKey)
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
            e.printStackTrace()
            null
        }
    }

    suspend fun saveOffsets(context: Context, sha1: String, layoutKey: String, offsets: List<Int>) = withContext(Dispatchers.IO) {
        val file = getCacheFile(context, sha1, layoutKey)
        try {
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
