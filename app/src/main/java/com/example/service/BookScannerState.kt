package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.io.FileOutputStream

object BookScannerState {
    val isScanning = MutableStateFlow(false)
    val scanProgressText = MutableStateFlow("")

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences("scanner_prefs", Context.MODE_PRIVATE)
        isScanning.value = prefs.getBoolean("is_scanning", false)
        scanProgressText.value = prefs.getString("progress_text", "") ?: ""
    }

    fun updateScanning(context: Context, active: Boolean, text: String) {
        isScanning.value = active
        scanProgressText.value = text
        
        context.getSharedPreferences("scanner_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("is_scanning", active)
            .putString("progress_text", text)
            .apply()
    }

    fun extractCover(file: File, sha1: String, context: Context): String? {
        val ext = file.extension.lowercase()
        var bitmap: Bitmap? = null
        try {
            if (ext == "fb2") {
                val bytes = file.readBytes()
                val headerSize = if (bytes.size > 1024 * 500) 1024 * 500 else bytes.size
                val fb2Content = String(bytes, 0, headerSize, java.nio.charset.Charset.forName("ISO-8859-1"))
                bitmap = extractCoverFromFb2(fb2Content)
            } else if (ext == "zip") {
                file.inputStream().use { fis ->
                    java.util.zip.ZipInputStream(fis).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val entryName = entry.name.lowercase()
                            if (!entry.isDirectory && entryName.endsWith(".fb2")) {
                                val bytes = zis.readBytes()
                                val headerSize = if (bytes.size > 1024 * 500) 1024 * 500 else bytes.size
                                val fb2Content = String(bytes, 0, headerSize, java.nio.charset.Charset.forName("ISO-8859-1"))
                                bitmap = extractCoverFromFb2(fb2Content)
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

    private fun extractCoverFromFb2(fb2Content: String): Bitmap? {
        try {
            val binaryBlockRegex = """<binary([^>]*)>([\s\S]*?)</binary>""".toRegex(RegexOption.IGNORE_CASE)
            val binaryDataMap = mutableMapOf<String, String>()
            for (match in binaryBlockRegex.findAll(fb2Content)) {
                val attrs = match.groups[1]?.value ?: ""
                val base64 = match.groups[2]?.value ?: ""
                val idMatch = """\bid\s*=\s*["']?([^"'\s>]+)["']?""".toRegex(RegexOption.IGNORE_CASE).find(attrs)
                val id = idMatch?.groups[1]?.value
                if (id != null) {
                    binaryDataMap[id.lowercase()] = base64
                }
            }

            var base64Data: String? = binaryDataMap["cover.jpg"] ?: binaryDataMap["cover"] ?: binaryDataMap["cover.png"]
            if (base64Data == null) {
                val key = binaryDataMap.keys.find { it.contains("cover") }
                if (key != null) {
                    base64Data = binaryDataMap[key]
                }
            }
            if (base64Data == null && binaryDataMap.isNotEmpty()) {
                base64Data = binaryDataMap.values.first()
            }

            if (base64Data != null) {
                val cleanBase64 = base64Data.replace("\\s".toRegex(), "")
                val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            }
        } catch (e: Exception) {
            android.util.Log.e("BookScannerState", "Error parsing FB2 XML for cover", e)
        }
        return null
    }
}
