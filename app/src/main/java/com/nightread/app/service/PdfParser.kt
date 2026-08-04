package com.nightread.app.service

import java.io.File
import java.util.regex.Pattern

object PdfParser : BookParser {
    override fun parse(file: File, defaultTitle: String): BookParser.ParsedBook {
        return try {
            val title = file.nameWithoutExtension.ifBlank { defaultTitle }
            val text = extractPdfText(file)
            val cleanText = if (text.isBlank()) "Файл PDF (Текст закодирован или содержит только изображения)" else text
            val htmlContent = "<html><body>" + cleanText.split("\n").filter { it.isNotBlank() }.joinToString("") { "<p>${escapeHtml(it)}</p>" } + "</body></html>"
            BookParser.ParsedBook(title, "PDF Документ", htmlContent)
        } catch (e: Exception) {
            BookParser.ParsedBook(file.nameWithoutExtension, "Неизвестен", "")
        }
    }

    private fun extractPdfText(file: File): String {
        val bytes = file.readBytes()
        val contentStr = String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1)
        val sb = java.lang.StringBuilder()
        
        val streamPattern = Pattern.compile("stream\\r?\\n(.*?)\\r?\\nendstream", Pattern.DOTALL)
        val streamMatcher = streamPattern.matcher(contentStr)
        
        while (streamMatcher.find()) {
            val streamContent = streamMatcher.group(1) ?: continue
            val btEtPattern = Pattern.compile("BT(.*?)ET", Pattern.DOTALL)
            val btEtMatcher = btEtPattern.matcher(streamContent)
            while (btEtMatcher.find()) {
                val textBlock = btEtMatcher.group(1) ?: continue
                val textPattern = Pattern.compile("\\((.*?)\\)")
                val textMatcher = textPattern.matcher(textBlock)
                val paragraph = java.lang.StringBuilder()
                while (textMatcher.find()) {
                    val part = textMatcher.group(1) ?: continue
                    val decoded = decodePdfString(part)
                    paragraph.append(decoded)
                }
                if (paragraph.isNotBlank()) {
                    sb.append(paragraph.toString().trim()).append("\n")
                }
            }
        }

        val lines = sb.toString().split("\n")
            .map { it.trim() }
            .filter { line ->
                line.length > 2 && line.count { it.isLetterOrDigit() || it == ' ' } > 0.4 * line.length
            }
        return lines.joinToString("\n")
    }

    private fun decodePdfString(str: String): String {
        var s = str.replace("\\)", ")")
            .replace("\\(", "(")
            .replace("\\\\", "\\")
            .replace("\\r", "")
            .replace("\\n", " ")
        
        val octalPattern = Pattern.compile("\\\\([0-7]{1,3})")
        val matcher = octalPattern.matcher(s)
        val sb = java.lang.StringBuffer()
        while (matcher.find()) {
            val octal = matcher.group(1) ?: ""
            try {
                val byteVal = octal.toInt(8).toChar()
                matcher.appendReplacement(sb, byteVal.toString())
            } catch (e: Exception) {
                matcher.appendReplacement(sb, "")
            }
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
