package com.nightread.app.data

import android.content.Context
import android.util.Log
import com.nightread.app.service.NewFb2Parser
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

object Sha1Helper {
    private const val TAG = "Sha1Helper"

    fun computeSha1(inputStream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
        val hash = digest.digest()
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun computeSha1FromContent(file: File): String? {
        return try {
            if (file.name.endsWith(".zip")) {
                ZipInputStream(file.inputStream()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.name.endsWith(".fb2")) {
                            return computeSha1(zip)
                        }
                        entry = zip.nextEntry
                    }
                }
                null
            } else {
                file.inputStream().use { computeSha1(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating SHA-1", e)
            null
        }
    }
}
