package com.nightread.app.data

import android.util.Log
import java.io.File
import java.util.zip.ZipInputStream

object EpubIdentifierHelper {
    private const val TAG = "EpubIdentifierHelper"

    fun isEpub(file: File): Boolean {
        return try {
            ZipInputStream(file.inputStream().buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "mimetype") {
                        val content = zip.readBytes().toString(Charsets.UTF_8).trim()
                        return content == "application/epub+zip"
                    }
                    entry = zip.nextEntry
                }
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if file is EPUB: ${file.name}", e)
            false
        }
    }

    fun getEpubIdentifier(file: File): String? {
        return try {
            var opfPath: String? = null
            ZipInputStream(file.inputStream().buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "META-INF/container.xml") {
                        val content = zip.readBytes().toString(Charsets.UTF_8)
                        val match = Regex("<rootfile\\s+[^>]*full-path\\s*=\\s*[\"']([^\"']+)[\"']").find(content)
                        opfPath = match?.groupValues?.get(1)
                    }
                    entry = zip.nextEntry
                }
            }
            
            if (opfPath != null) {
                ZipInputStream(file.inputStream().buffered()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.name == opfPath) {
                            val content = zip.readBytes().toString(Charsets.UTF_8)
                            val match = Regex("<dc:identifier[^>]*>([^<]+)</dc:identifier>", RegexOption.IGNORE_CASE).find(content)
                            return match?.groupValues?.get(1)?.trim()
                        }
                        entry = zip.nextEntry
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting EPUB identifier: ${file.name}", e)
            null
        }
    }
}
