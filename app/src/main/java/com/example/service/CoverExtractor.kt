package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

object CoverExtractor {

    private const val TAG = "CoverExtractor"

    fun extractCover(file: File, sha1: String, context: Context?): String? {
        Log.d(TAG, "extractCover called for ${file.name}, sha1=$sha1, context=$context")
        
        if (context == null) {
            Log.e(TAG, "Context is null, cannot extract cover")
            return null
        }
        
        val ext = file.extension.lowercase()
        var bitmap: Bitmap? = null
        
        try {
            Log.d(TAG, "Parsing file: ${file.absolutePath}")
            if (ext == "fb2") {
                val bytes = file.readBytes()
                val fb2Content = decodeBytesToString(bytes)
                bitmap = Fb2Parser.extractCover(fb2Content)
            } else if (ext == "zip") {
                file.inputStream().use { fis ->
                    ZipInputStream(fis).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val entryName = entry.name.lowercase()
                            if (!entry.isDirectory && entryName.endsWith(".fb2")) {
                                val bytes = zis.readBytes()
                                val fb2Content = decodeBytesToString(bytes)
                                bitmap = Fb2Parser.extractCover(fb2Content)
                                break
                            }
                            entry = zis.nextEntry
                        }
                    }
                }
            }
            
            if (bitmap != null) {
                Log.d(TAG, "Cover found, saving to cache")
                val cacheDir = File(context.cacheDir, "covers")
                if (!cacheDir.exists()) {
                    if (!cacheDir.mkdirs()) {
                        Log.e(TAG, "Failed to create cache directory: ${cacheDir.absolutePath}")
                        return null
                    }
                }
                val coverFile = File(cacheDir, "${sha1}.jpg")
                FileOutputStream(coverFile).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                }
                Log.d(TAG, "Cover saved: ${coverFile.absolutePath}")
                return coverFile.absolutePath
            } else {
                Log.d(TAG, "No cover found for ${file.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting cover for ${file.name}", e)
        }
        return null
    }

    private fun decodeBytesToString(bytes: ByteArray): String {
        try {
            val headerSize = if (bytes.size > 1024) 1024 else bytes.size
            val header = String(bytes, 0, headerSize, java.nio.charset.StandardCharsets.ISO_8859_1)
            val match = """encoding=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE).find(header)
            if (match != null) {
                val encName = match.groupValues[1].trim()
                try {
                    return String(bytes, java.nio.charset.Charset.forName(encName))
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {}

        try {
            val utf8Decoder = java.nio.charset.StandardCharsets.UTF_8.newDecoder()
            utf8Decoder.onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            val charBuffer = utf8Decoder.decode(java.nio.ByteBuffer.wrap(bytes))
            return charBuffer.toString()
        } catch (e: Exception) {
            try {
                return String(bytes, java.nio.charset.Charset.forName("Windows-1251"))
            } catch (e2: Exception) {
                return String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1)
            }
        }
    }
}
