package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.io.FileOutputStream

object BookScannerState {
    val isScanning: MutableStateFlow<Boolean>
        get() = BookScanState.isScanning

    val scanProgressText: MutableStateFlow<String>
        get() = BookScanState.scanProgressText

    fun initialize(context: Context) {
        BookScanState.initialize(context)
    }

    fun updateScanning(context: Context, active: Boolean, text: String) {
        BookScanState.updateScanning(context, active, text, 
            total = BookScanState.totalFiles.value, 
            processed = BookScanState.processedFiles.value, 
            error = BookScanState.errorText.value
        )
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

    fun extractCover(file: File, sha1: String, context: Context): String? {
        val ext = file.extension.lowercase()
        var bitmap: Bitmap? = null
        try {
            if (ext == "fb2") {
                val bytes = file.readBytes()
                val fb2Content = decodeBytesToString(bytes)
                bitmap = Fb2Parser.extractCover(fb2Content)
            } else if (ext == "zip") {
                file.inputStream().use { fis ->
                    java.util.zip.ZipInputStream(fis).use { zis ->
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
                val cacheDir = File(context.cacheDir, "covers")
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs()
                }
                val coverFile = File(cacheDir, "${sha1}.jpg")
                FileOutputStream(coverFile).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                }
                return coverFile.absolutePath
            }
        } catch (e: Exception) {
            android.util.Log.e("BookScannerState", "Error extracting cover", e)
        }
        return null
    }
}
