package com.nightread.app.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets

object PdfParser : BookParser {
    private const val TAG = "PdfParser"

    override fun parse(file: File, defaultTitle: String): BookParser.ParsedBook {
        val title = extractTitle(file, defaultTitle)
        val author = extractAuthor(file)
        val content = extractText(file)
        val coverBytes = extractCoverBytes(file)
        return BookParser.ParsedBook(
            title = title,
            author = author,
            content = TextCleaner.cleanText(content) as String,
            notes = emptyMap(),
            coverBytes = coverBytes
        )
    }

    private fun extractTitle(file: File, defaultTitle: String): String {
        try {
            val content = file.inputStream().use { it.readBytes() }
            val text = String(content, StandardCharsets.ISO_8859_1)
            val titleRegex = Regex("""/Title\s*\((.*?)\)""", RegexOption.IGNORE_CASE)
            val match = titleRegex.find(text)
            if (match != null) {
                val rawTitle = match.groupValues[1]
                val decoded = decodePdfString(rawTitle)
                if (decoded.isNotBlank()) return decoded
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting PDF title: ${e.message}")
        }
        return defaultTitle
    }

    private fun extractAuthor(file: File): String {
        try {
            val content = file.inputStream().use { it.readBytes() }
            val text = String(content, StandardCharsets.ISO_8859_1)
            val authorRegex = Regex("""/Author\s*\((.*?)\)""", RegexOption.IGNORE_CASE)
            val match = authorRegex.find(text)
            if (match != null) {
                val rawAuthor = match.groupValues[1]
                val decoded = decodePdfString(rawAuthor)
                if (decoded.isNotBlank()) return decoded
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting PDF author: ${e.message}")
        }
        return "Неизвестен"
    }

    private fun decodePdfString(str: String): String {
        // Simple octal decode: \344\345\356 -> decoded chars
        val octalRegex = Regex("""\\([0-7]{3})""")
        var decoded = str
        try {
            decoded = octalRegex.replace(str) { matchResult ->
                val octalStr = matchResult.groupValues[1]
                val charVal = octalStr.toInt(8).toChar()
                charVal.toString()
            }
        } catch (e: Exception) {
            // ignore
        }
        
        // Convert Windows-1251 if needed
        val hasWin1251 = decoded.any { it.code in 192..255 }
        if (hasWin1251) {
            try {
                val bytes = decoded.map { it.code.toByte() }.toByteArray()
                return String(bytes, java.nio.charset.Charset.forName("windows-1251"))
            } catch (e: Exception) {
                // ignore
            }
        }
        return decoded
    }

    fun extractText(file: File): String {
        val sb = StringBuilder()
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            val pageCount = renderer.pageCount
            sb.append("PDF Документ: ${file.name}\n")
            sb.append("Количество страниц: $pageCount\n\n")
            
            val contentBytes = file.inputStream().use { it.readBytes() }
            val text = String(contentBytes, StandardCharsets.ISO_8859_1)
            
            val btEtRegex = Regex("""BT(.*?)ET""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            val textRegex = Regex("""\((.*?)\)""")
            
            var extractedTextCount = 0
            for (btEtMatch in btEtRegex.findAll(text)) {
                val btEtBlock = btEtMatch.groupValues[1]
                for (textMatch in textRegex.findAll(btEtBlock)) {
                    val rawText = textMatch.groupValues[1]
                    val decoded = decodePdfString(rawText)
                    if (decoded.isNotBlank()) {
                        sb.append(decoded).append(" ")
                        extractedTextCount++
                    }
                }
                sb.append("\n")
                if (extractedTextCount > 20000) {
                    sb.append("\n[Текст сокращен из-за большого размера]\n")
                    break
                }
            }
            
            if (extractedTextCount < 50) {
                sb.append("Чтение графических или сжатых PDF пока доступно в виде постраничного описания.\n")
                sb.append("Вы можете просматривать информацию о книге и синхронизировать её со всеми устройствами.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting PDF text: ${e.message}", e)
            sb.append("Не удалось извлечь текст из PDF: ${e.message}")
        } finally {
            try {
                renderer?.close()
                pfd?.close()
            } catch (e: Exception) {}
        }
        return sb.toString()
    }

    fun extractCoverBytes(file: File): ByteArray? {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            if (renderer.pageCount > 0) {
                val page = renderer.openPage(0)
                val scale = 400.0f / page.width.toFloat()
                val targetW = 400
                val targetH = (page.height * scale).toInt()
                
                val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                
                val bos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, bos)
                return bos.toByteArray()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting PDF cover: ${e.message}", e)
        } finally {
            try {
                renderer?.close()
                pfd?.close()
            } catch (e: Exception) {}
        }
        return null
    }
}
