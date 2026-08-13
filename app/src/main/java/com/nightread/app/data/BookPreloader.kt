package com.nightread.app.data

import android.content.Context
import com.nightread.app.service.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

object BookPreloader {

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

                val theme = SettingsManager.getReadingTheme(appContext)
                val fontSize = SettingsManager.getFontSize(appContext)
                val lineSpacing = SettingsManager.getLineSpacing(appContext)
                val fontFamily = SettingsManager.getFontFamily(appContext)
                val fontWeight = SettingsManager.getFontWeightAsInt(appContext)

                val ext = file.extension.lowercase()
                val rawContent = if (contentCacheFile.exists()) {
                    contentCacheFile.readText()
                } else {
                    val parsed = if (ext == "zip" || ext == "fbz" || file.name.endsWith(".fb2.zip", true) || file.name.endsWith(".fb3.zip", true)) {
                        readZipContent(file)
                    } else {
                        when (ext) {
                            "fb3" -> Fb3Parser.parse(file, file.nameWithoutExtension).content
                            "epub" -> EpubParser.parse(file, file.nameWithoutExtension).content
                            "mobi", "azw", "azw3" -> MobiParser.parse(file, file.nameWithoutExtension).content
                            "fb2" -> decodeBytesToString(file.readBytes())
                            else -> decodeBytesToString(file.readBytes())
                        }
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
            } catch (e: Throwable) {
                android.util.Log.e("BookPreloader", "Preloading failed for $filePath", e)
            }
        }
    }

    private fun readZipContent(file: File): String {
        try {
            if (Fb3Parser.isFb3(file)) {
                return Fb3Parser.parseFb3(file, file.nameWithoutExtension).content
            }
            java.io.FileInputStream(file).use { fis ->
                java.util.zip.ZipInputStream(fis).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val entryName = entry.name.lowercase()
                        if (!entry.isDirectory && !entryName.startsWith("__macosx") && !entryName.contains(".ds_store")) {
                            if (entryName.endsWith(".fb3")) {
                                val bytes = zis.readBytes()
                                return Fb3Parser.parseBytes(bytes, entryName.removeSuffix(".fb3")).content
                            } else if (entryName.endsWith(".fb2")) {
                                val parsed = Fb2Parser.parse(zis, entryName.removeSuffix(".fb2"))
                                if (parsed.content.isNotBlank()) return parsed.content
                            } else if (entryName.endsWith(".xml") || entryName.endsWith(".html") || entryName.endsWith(".htm") || entryName.endsWith(".txt")) {
                                return decodeBytesToString(zis.readBytes())
                            }
                        }
                        entry = zis.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("BookPreloader", "Error preloading zip file ${file.name}", e)
        }
        return ""
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
                } catch (e: Exception) {
                    // fall back if charset name is invalid or unsupported
                }
            }
        } catch (e: Exception) {
            // ignore and fallback
        }

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
