package com.nightread.app.service

import java.io.File
import java.util.zip.ZipFile
import java.util.regex.Pattern

object DocxParser : BookParser {
    override fun parse(file: File, defaultTitle: String): BookParser.ParsedBook {
        return try {
            val title = file.nameWithoutExtension.ifBlank { defaultTitle }
            val text = extractTextFromDocx(file)
            val htmlContent = "<html><body>" + text.split("\n").filter { it.isNotBlank() }.joinToString("") { "<p>${escapeHtml(it)}</p>" } + "</body></html>"
            BookParser.ParsedBook(title, "Word Документ (DOCX)", htmlContent)
        } catch (e: Exception) {
            BookParser.ParsedBook(file.nameWithoutExtension, "Неизвестен", "")
        }
    }

    private fun extractTextFromDocx(file: File): String {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("word/document.xml") ?: return ""
            zip.getInputStream(entry).use { stream ->
                val xml = stream.bufferedReader(java.nio.charset.StandardCharsets.UTF_8).readText()
                val sb = java.lang.StringBuilder()
                val pPattern = Pattern.compile("<w:p(?: [^>]*)?>(.*?)</w:p>")
                val tPattern = Pattern.compile("<w:t(?: [^>]*)?>(.*?)</w:t>")
                
                val pMatcher = pPattern.matcher(xml)
                while (pMatcher.find()) {
                    val pContent = pMatcher.group(1) ?: continue
                    val tMatcher = tPattern.matcher(pContent)
                    val paragraphText = java.lang.StringBuilder()
                    while (tMatcher.find()) {
                        val text = tMatcher.group(1)
                        if (text != null) {
                            paragraphText.append(decodeXmlEntities(text))
                        }
                    }
                    if (paragraphText.isNotEmpty()) {
                        sb.append(paragraphText.toString()).append("\n")
                    }
                }
                return sb.toString()
            }
        }
    }

    private fun decodeXmlEntities(text: String): String {
        return text.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
