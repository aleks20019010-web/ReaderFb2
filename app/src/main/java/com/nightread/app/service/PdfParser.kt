package com.nightread.app.service

import java.io.File
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.parser.PdfTextExtractor

object PdfParser : BookParser {
    override fun parse(file: File, defaultTitle: String): BookParser.ParsedBook {
        return try {
            val title = file.nameWithoutExtension.ifBlank { defaultTitle }
            val text = extractPdfText(file)
            val cleanText = if (text.isBlank()) "Файл PDF (Текст закодирован или содержит только изображения)" else text
            val htmlContent = "<html><body>" + cleanText.split("\n").filter { it.isNotBlank() }.joinToString("") { "<p>${escapeHtml(it)}</p>" } + "</body></html>"
            val preview = makePreview(cleanText)
            BookParser.ParsedBook(title, "PDF Документ", htmlContent, annotation = preview)
        } catch (e: Exception) {
            BookParser.ParsedBook(file.nameWithoutExtension, "Неизвестен", "")
        }
    }

    private fun makePreview(rawText: String): String {
        val clean = rawText.replace(Regex("<[^>]*>"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        return if (clean.length > 180) clean.take(180) + "..." else clean
    }

    private fun extractPdfText(file: File): String {
        val sb = java.lang.StringBuilder()
        try {
            val reader = PdfReader(file.absolutePath)
            val pages = reader.numberOfPages
            for (i in 1..pages) {
                val pageText = PdfTextExtractor.getTextFromPage(reader, i)
                if (pageText.isNotBlank()) {
                    sb.append(pageText).append("\n\n")
                }
            }
            reader.close()
        } catch (e: Exception) {
            android.util.Log.e("PdfParser", "Error extracting PDF text", e)
        }
        return sb.toString().trim()
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
