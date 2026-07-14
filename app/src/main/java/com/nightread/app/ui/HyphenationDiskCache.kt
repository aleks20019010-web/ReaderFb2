package com.nightread.app.ui

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

object HyphenationDiskCache {
    private fun getCacheDir(context: Context): File {
        val dir = File(context.cacheDir, "hyphenation_cache")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getCacheFile(context: Context, sha1: String): File {
        val digest = MessageDigest.getInstance("MD5")
        val hashBytes = digest.digest(sha1.toByteArray(Charsets.UTF_8))
        val hashString = hashBytes.joinToString("") { "%02x".format(it) }
        return File(getCacheDir(context), "$hashString.txt")
    }

    suspend fun getHyphenatedText(context: Context, sha1: String): String? = withContext(Dispatchers.IO) {
        val file = getCacheFile(context, sha1)
        if (!file.exists()) return@withContext null
        try {
            file.readText()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun saveHyphenatedText(context: Context, sha1: String, text: String) = withContext(Dispatchers.IO) {
        val file = getCacheFile(context, sha1)
        try {
            val tempFile = File(file.parent, file.name + ".tmp")
            tempFile.writeText(text)
            tempFile.renameTo(file)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
