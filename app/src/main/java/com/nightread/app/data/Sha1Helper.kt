package com.nightread.app.data

import android.content.Context
import android.util.Log
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

object Sha1Helper {
    private const val TAG = "Sha1Helper"

    fun computeSha1(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun computeSha1Stream(inputStream: InputStream): String {
        val buffer = java.io.ByteArrayOutputStream()
        val data = ByteArray(8192)
        var nRead: Int
        while (inputStream.read(data, 0, data.size).also { nRead = it } != -1) {
            buffer.write(data, 0, nRead)
        }
        return computeSha1(buffer.toByteArray())
    }

    fun computeSha1FromContent(file: File): String? {
        return try {
            val lowerName = file.name.lowercase()
            if (lowerName.endsWith(".fb2.zip") || lowerName.endsWith(".zip")) {
                ZipInputStream(file.inputStream().buffered()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.name.lowercase().endsWith(".fb2")) {
                            return computeSha1Stream(zip)
                        }
                        entry = zip.nextEntry
                    }
                }
                // Fallback to computing the SHA-1 of the zip archive itself
                file.inputStream().buffered().use { computeSha1Stream(it) }
            } else {
                file.inputStream().buffered().use { computeSha1Stream(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating SHA-1", e)
            null
        }
    }
}
