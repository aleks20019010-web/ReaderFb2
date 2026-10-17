package com.nightread.app.data

import android.content.Context
import com.nightread.app.service.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

object BookPreloader {

    private const val MAX_PRELOAD_FILE_BYTES = 30L * 1024L * 1024L
    private const val MAX_PRELOAD_TEXT_BYTES = 8L * 1024L * 1024L

    fun preload(context: Context, sha1: String, filePath: String?) {
        if (filePath.isNullOrEmpty() || sha1.isEmpty()) return
        val appContext = context.applicationContext
        val cacheHtmlFile = File(appContext.cacheDir, "$sha1.html")
        val contentCacheFile = File(appContext.cacheDir, "$sha1.content")

        if (cacheHtmlFile.exists() && contentCacheFile.exists()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val file = File(filePath)
                if (!file.exists()) return@launch
                if (file.length() > MAX_PRELOAD_FILE_BYTES) {
                    android.util.Log.w("BookPreloader", "Skipping preload for large file: ${file.name} (${file.length()} bytes)")
                    return@launch
                }

                val theme = SettingsManager.getReadingTheme(appContext)
                val fontSize = SettingsManager.getFontSize(appContext)
                val lineSpacing = SettingsManager.getLineSpacing(appContext)
                val fontFamily = SettingsManager.getFontFamily(appContext)
                val fontWeight = SettingsManager.getFontWeightAsInt(appContext)

                val ext = file.extension.lowercase()
                val rawContent = if (contentCacheFile.exists()) {
                    contentCacheFile.readText()
                } else {
                    val parsed = when {
                        ext == "zip" || ext == "fbz" || file.name.endsWith(".fb2.zip", true) || file.name.endsWith(".fb3.zip", true) -> readZipContent(file)
                        ext == "fb3" -> Fb3Parser.parse(file, file.nameWithoutExtension).content
                        ext == "epub" -> EpubParser.parse(file, file.nameWithoutExtension).content
                        ext == "mobi" || ext == "azw" || ext == "azw3" -> MobiParser.parse(file, file.nameWithoutExtension).content
                        ext == "fb2" -> {
                            try {
                                FileInputStream(file).use { fis ->
                                    Fb2Parser.parse(fis, file.nameWithoutExtension).content
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("BookPreloader", "FB2 stream parse failed for ${file.name}", e)
                                try {
                                    Fb2Parser.parse(file.inputStream(), file.nameWithoutExtension).content
                                } catch (e2: Exception) {
                                    ""
                                }
                            }
                        }
                        else -> decodeFileToStringWithLimit(file)
                    }

                    if (parsed.length > MAX_PRELOAD_TEXT_BYTES) {
                        android.util.Log.w("BookPreloader", "Parsed content too large for preload cache: ${file.name}")
                        return@launch
                    }
                    try { contentCacheFile.writeText(parsed) } catch (e: Exception) {}
                    parsed
                }

                if (!cacheHtmlFile.exists()) {
                    val paragraphIndent = SettingsManager.getParagraphIndent(appContext)
                    val converted = if (ext == "fb2" || file.name.endsWith(".fb2.zip", true) || file.name.endsWith(".zip", true) || ext == "fbz") {
                        Fb2ToHtmlConverterAdvanced.convert(
                            fb2Xml = rawContent,
                            theme = theme,
                            fontSize = fontSize,
                            lineSpacing = lineSpacing,
                            fontFamily = fontFamily,
                            fontWeight = fontWeight,
                            fontAlignment = "JUSTIFY",
                            pageMargins = true,
                            paddingTop = 15,
                            paddingBottom = 16,
                            paddingLeft = 8,
                            paddingRight = 8,
                            paragraphIndent = paragraphIndent
                        )
                    } else {
                        EpubToHtmlConverter.convert(
                            xhtmlContent = rawContent,
                            theme = theme,
                            fontSize = fontSize,
                            lineSpacing = lineSpacing,
                            fontFamily = fontFamily,
                            fontWeight = fontWeight,
                            fontAlignment = "JUSTIFY",
                            pageMargins = true,
                            paddingTop = 15,
                            paddingBottom = 16,
                            paddingLeft = 8,
                            paddingRight = 8,
                            paragraphIndent = paragraphIndent
                        )
                    }
                    try { cacheHtmlFile.writeText(converted) } catch (e: Exception) {}
                }
            } catch (e: OutOfMemoryError) {
                android.util.Log.e("BookPreloader", "Out of memory while preloading $filePath", e)
                System.gc()
            } catch (e: Throwable) {
                android.util.Log.e("BookPreloader", "Preloading failed for $filePath", e)
            }
        }
    }

    private fun readZipContent(file: File): String {
        try {
            if (file.length() > MAX_PRELOAD_FILE_BYTES) {
                android.util.Log.w("BookPreloader", "Skipping zip preload for large file: ${file.name}")
                return ""
            }
            if (Fb3Parser.isFb3(file)) {
                return Fb3Parser.parseFb3(file, file.nameWithoutExtension).content
            }

            FileInputStream(file).use { fis ->
                ZipInputStream(fis).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val entryName = entry.name.lowercase()
                        if (!entry.isDirectory && !entryName.startsWith("__macosx") && !entryName.contains(".ds_store")) {
                            when {
                                entryName.endsWith(".fb3") -> {
                                    val bytes = zis.readBytes()
                                    return Fb3Parser.parseBytes(bytes, entryName.removeSuffix(".fb3")).content
                                }
                                entryName.endsWith(".fb2") -> {
                                    val parsed = Fb2Parser.parse(zis, entryName.removeSuffix(".fb2"))
                                    if (parsed.content.isNotBlank()) return parsed.content
                                }
                                entryName.endsWith(".xml") || entryName.endsWith(".html") || entryName.endsWith(".htm") || entryName.endsWith(".txt") -> {
                                    return decodeStreamToStringWithLimit(zis)
                                }
                            }
                        }
                        entry = zis.nextEntry
                    }
                }
            }
        } catch (e: OutOfMemoryError) {
            android.util.Log.e("BookPreloader", "Out of memory while reading zip ${file.name}", e)
            System.gc()
        } catch (e: Exception) {
            android.util.Log.e("BookPreloader", "Error preloading zip file ${file.name}", e)
        }
        return ""
    }

    private fun decodeFileToStringWithLimit(file: File, maxBytes: Int = 5 * 1024 * 1024): String {
        try {
            FileInputStream(file).use { fis ->
                return decodeStreamToStringWithLimit(fis, maxBytes)
            }
        } catch (e: OutOfMemoryError) {
            android.util.Log.e("BookPreloader", "Out of memory while decoding file ${file.name}", e)
            System.gc()
        } catch (e: Exception) {
            android.util.Log.e("BookPreloader", "Failed to decode file ${file.name}", e)
        }
        return ""
    }

    private fun decodeStreamToStringWithLimit(input: InputStream, maxBytes: Int = 5 * 1024 * 1024): String {
        try {
            val buffer = ByteArrayOutputStream()
            val tmp = ByteArray(8 * 1024)
            var total = 0
            var read = input.read(tmp)
            while (read != -1 && total < maxBytes) {
                val toWrite = if (total + read > maxBytes) maxBytes - total else read
                buffer.write(tmp, 0, toWrite)
                total += toWrite
                if (toWrite < read) break
                read = input.read(tmp)
            }
            val bytes = buffer.toByteArray()

            try {
                val headerSize = if (bytes.size > 1024) 1024 else bytes.size
                val header = String(bytes, 0, headerSize, java.nio.charset.StandardCharsets.ISO_8859_1)
                val match = """encoding=[\"']([^\"']+)[\"']""".toRegex(RegexOption.IGNORE_CASE).find(header)
                if (match != null) {
                    val encName = match.groupValues[1].trim()
                    try {
                        return String(bytes, java.nio.charset.Charset.forName(encName))
                    } catch (_: Exception) {
                        // fall through
                    }
                }
            } catch (_: Exception) {
                // ignore and fallback
            }

            try {
                val utf8Decoder = java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                utf8Decoder.onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                val charBuffer = utf8Decoder.decode(java.nio.ByteBuffer.wrap(bytes))
                return charBuffer.toString()
            } catch (e: OutOfMemoryError) {
                android.util.Log.e("BookPreloader", "Out of memory while decoding UTF-8 string", e)
                System.gc()
            } catch (e: Exception) {
                try {
                    return String(bytes, java.nio.charset.Charset.forName("Windows-1251"))
                } catch (_: Exception) {
                    return String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1)
                }
            }
        } catch (e: OutOfMemoryError) {
            android.util.Log.e("BookPreloader", "Out of memory while decoding stream", e)
            System.gc()
        } catch (e: Exception) {
            android.util.Log.e("BookPreloader", "Failed to decode stream", e)
        }

        return ""
    }
}
