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
        val digest = MessageDigest.getInstance("SHA-1")
        val data = ByteArray(65536)
        var nRead: Int
        while (inputStream.read(data, 0, data.size).also { nRead = it } != -1) {
            digest.update(data, 0, nRead)
        }
        val hash = digest.digest()
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun computeSha1FileNio(file: File): String? {
        return try {
            file.inputStream().buffered().use { computeSha1Stream(it) }
        } catch (e: Throwable) {
            Log.e(TAG, "Error in computeSha1FileNio for ${file.name}", e)
            null
        }
    }

    fun computeSha1FromContent(file: File): String? {
        return try {
            val lowerName = file.name.lowercase()
            if (lowerName.endsWith(".fb2.zip") || lowerName.endsWith(".fb3.zip") || lowerName.endsWith(".zip")) {
                ZipInputStream(file.inputStream().buffered()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val entryName = entry.name.lowercase()
                        if (entryName.endsWith(".fb2") || entryName.endsWith(".fb3")) {
                            return computeSha1Stream(zip)
                        }
                        entry = zip.nextEntry
                    }
                }
                // Fallback to computing the SHA-1 of the zip archive itself
                computeSha1FileNio(file)
            } else {
                computeSha1FileNio(file)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating SHA-1", e)
            null
        }
    }
}
