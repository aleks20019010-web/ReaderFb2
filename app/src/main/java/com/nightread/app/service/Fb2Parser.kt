package com.nightread.app.service

import android.util.Log
import java.io.File

object Fb2Parser : BookParser {

    override fun parse(file: File, defaultTitle: String): BookParser.ParsedBook {
        try {
            val bytes = file.readBytes()
            val xmlContent = decodeFb2Bytes(bytes)
            return parseFb2String(xmlContent, defaultTitle)
        } catch (e: Exception) {
            Log.e("Fb2Parser", "Error parsing FB2", e)
            return BookParser.ParsedBook(defaultTitle, "Неизвестен", "")
        }
    }

    private fun decodeFb2Bytes(bytes: ByteArray): String {
        val header = String(bytes, 0, minOf(bytes.size, 1024), java.nio.charset.StandardCharsets.ISO_8859_1)
        val match = Regex("""<\?xml[^>]*encoding=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(header)
        val charsetName = match?.groupValues?.get(1)?.trim() ?: "UTF-8"
        return try {
            String(bytes, java.nio.charset.Charset.forName(charsetName))
        } catch (e: Exception) {
            String(bytes, java.nio.charset.StandardCharsets.UTF_8)
        }
    }

    fun parseFb2String(xmlContent: String, defaultTitle: String): BookParser.ParsedBook {
        val notesMap = mutableMapOf<String, String>()
        
        // Extract notes body if present to avoid mixing notes text into main body
        var mainXml = xmlContent
        var notesXml = ""
        val notesBodyStart = xmlContent.indexOf("<body name=\"notes\"", ignoreCase = true).let { 
            if (it == -1) xmlContent.indexOf("<body id=\"notes\"", ignoreCase = true) else it
        }
        if (notesBodyStart != -1) {
            val notesBodyEnd = xmlContent.indexOf("</body>", notesBodyStart, ignoreCase = true)
            if (notesBodyEnd != -1) {
                notesXml = xmlContent.substring(notesBodyStart, notesBodyEnd + "</body>".length)
                mainXml = xmlContent.removeRange(notesBodyStart, notesBodyEnd + "</body>".length)
            }
        }
        
        // Extract notes from sections with id
        val sectionRegex = Regex("""<section[^>]*id=["']([^"']+)["'][^>]*>(.*?)</section>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        
        // Search in both notesXml and mainXml (as fallback)
        val searchXmls = listOf(notesXml, mainXml)
        for (xml in searchXmls) {
            if (xml.isEmpty()) continue
            for (match in sectionRegex.findAll(xml)) {
                val id = match.groupValues[1]
                var inner = match.groupValues[2]
                // Strip tags from note content
                inner = inner.replace(Regex("<[^>]+>"), " ")
                inner = decodeHtmlEntities(inner).trim()
                if (inner.isNotEmpty()) {
                    notesMap[id] = inner
                }
            }
        }

        // Now parse metadata
        var title = defaultTitle
        val titleMatch = Regex("""<book-title[^>]*>(.*?)</book-title>""", RegexOption.IGNORE_CASE).find(mainXml)
        if (titleMatch != null) {
            title = titleMatch.groupValues[1].replace(Regex("<[^>]+>"), "").trim()
        }
        
        var author = "Неизвестен"
        val firstNameMatch = Regex("""<first-name[^>]*>(.*?)</first-name>""", RegexOption.IGNORE_CASE).find(mainXml)
        val lastNameMatch = Regex("""<last-name[^>]*>(.*?)</last-name>""", RegexOption.IGNORE_CASE).find(mainXml)
        if (firstNameMatch != null || lastNameMatch != null) {
            val fn = firstNameMatch?.groupValues?.get(1)?.replace(Regex("<[^>]+>"), "")?.trim() ?: ""
            val ln = lastNameMatch?.groupValues?.get(1)?.replace(Regex("<[^>]+>"), "")?.trim() ?: ""
            author = "$fn $ln".trim().ifEmpty { "Неизвестен" }
        }

        // Get main content body
        val bodyStart = mainXml.indexOf("<body>")
        val bodyEnd = mainXml.indexOf("</body>")
        val contentToParse = if (bodyStart != -1 && bodyEnd != -1 && bodyEnd > bodyStart) {
            mainXml.substring(bodyStart, bodyEnd + "</body>".length)
        } else {
            mainXml
        }

        var text = contentToParse

        // Replace cites/blockquote
        text = text
            .replace(Regex("<cite[^>]*>", RegexOption.IGNORE_CASE), "\n[CITE]")
            .replace(Regex("</cite>", RegexOption.IGNORE_CASE), "[/CITE]\n")

        // Replace sup tags
        text = text
            .replace(Regex("<sup[^>]*>", RegexOption.IGNORE_CASE), "[SUP]")
            .replace(Regex("</sup>", RegexOption.IGNORE_CASE), "[/SUP]")

        // Replace links to notes: <a l:href="#note_id">1</a>
        val noteLinkRegex = Regex("""<a[^>]*(?:href|l:href)=["']#([^"']+)["'][^>]*>(.*?)</a>""", RegexOption.IGNORE_CASE)
        text = noteLinkRegex.replace(text) { matchResult ->
            val noteId = matchResult.groupValues[1]
            val linkText = matchResult.groupValues[2].replace(Regex("<[^>]+>"), "")
            "[NOTE:$noteId]$linkText[/NOTE]"
        }

        // Map rest of formatting
        text = text
            .replace(Regex("<empty-line[^>]*>"), "\n")
            .replace(Regex("<title[^>]*>"), "\n\u000C[CHAPTER]")
            .replace(Regex("</title>"), "[/CHAPTER]\n")
            .replace(Regex("<p[^>]*>"), "\n\u200B\u200B\u200B\u200B")
            .replace(Regex("</p>"), "")
            .replace(Regex("<v[^>]*>"), "\n\u200B\u200B\u200B\u200B")
            .replace(Regex("</v>"), "")
            .replace(Regex("<subtitle[^>]*>"), "\n")
            .replace(Regex("</subtitle>"), "\n")
            .replace(Regex("<hyphen[^>]*>"), "\u00AD")

        // Strip remaining tags
        text = text.replace(Regex("<[^>]+>"), "")

        // Decode entities
        text = decodeHtmlEntities(text)

        // Clean up multiple newlines to just one newline, excluding FormFeed (\u000C)
        text = text.replace(Regex("([ \\t\\r\\n]*\\n[ \\t\\r\\n]*)+"), "\n\u200B\u200B\u200B\u200B")

        // Clean up consecutive page breaks
        text = text.replace(Regex("\\u000C+"), "\u000C")

        val finalResult = text.trim().trim('\u000C').trim()
        
        return BookParser.ParsedBook(
            title = title,
            author = author,
            content = finalResult,
            notes = notesMap
        )
    }

    private fun decodeHtmlEntities(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace("&laquo;", "«")
            .replace("&raquo;", "»")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
    }

    // Retain old method for backward compatibility
    fun extractText(xmlContent: String): String {
        return parseFb2String(xmlContent, "").content
    }
}
