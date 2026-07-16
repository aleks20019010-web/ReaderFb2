package com.nightread.app.service

import java.io.File
import com.nightread.app.service.BookParser.ParsedBook

data class BookMetadata(
    val title: String,
    val author: String,
    val content: String,
    val series: String?,
    val seriesIndex: Int?,
    val language: String?,
    val annotation: String? = null
)

object Fb2Parser : BookParser {
    fun extractAnnotation(fb2Content: String): String? {
        return try {
            val annotationMatch = Regex("<annotation>\\s*(.*?)\\s*</annotation>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).find(fb2Content)
            if (annotationMatch != null) {
                var annotationText = annotationMatch.groupValues[1]
                // Strip tags like <p>, <strong>, etc.
                annotationText = annotationText.replace(Regex("<[^>]+>"), " ")
                // Decode common XML entities
                annotationText = annotationText
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&quot;", "\"")
                    .replace("&apos;", "'")
                
                annotationText.split("\n")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .joinToString("\n")
                    .trim()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun parse(file: File, defaultTitle: String): ParsedBook {
        return file.inputStream().use { inputStream ->
            val metadata = parse(inputStream, defaultTitle)
            // For file-based parsing, we also need to read content.
            // The InputStream-based parse only reads header for metadata.
            // This is a bit of a workaround to maintain compatibility.
            val fullContent = file.readText(Charsets.UTF_8)
            ParsedBook(
                title = metadata.title,
                author = metadata.author,
                content = TextCleaner.cleanText(fullContent) as String
            )
        }
    }

    fun parse(inputStream: java.io.InputStream, defaultTitle: String): BookMetadata {
        return try {
            // Read only the header part for metadata
            val buffer = ByteArray(150000)
            val bytesRead = inputStream.read(buffer)
            val headerContent = if (bytesRead != -1) String(buffer, 0, bytesRead, Charsets.UTF_8) else ""
            
            var title = ""
            var firstName = ""
            var lastName = ""
            var series: String? = null
            var seriesIndex: Int? = null
            var lang: String? = null
            
            // Use regex for extremely fast and robust parsing of metadata
            val titleMatch = Regex("<book-title>\\s*([^<]+?)\\s*</book-title>", RegexOption.IGNORE_CASE).find(headerContent)
            if (titleMatch != null) {
                title = titleMatch.groupValues[1].trim()
            }
            
            val authorMatch = Regex("<author>\\s*(.*?)\\s*</author>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).find(headerContent)
            if (authorMatch != null) {
                val authorBlock = authorMatch.groupValues[1]
                val fnMatch = Regex("<first-name>\\s*([^<]+?)\\s*</first-name>", RegexOption.IGNORE_CASE).find(authorBlock)
                val lnMatch = Regex("<last-name>\\s*([^<]+?)\\s*</last-name>", RegexOption.IGNORE_CASE).find(authorBlock)
                if (fnMatch != null) firstName = fnMatch.groupValues[1].trim()
                if (lnMatch != null) lastName = lnMatch.groupValues[1].trim()
            }
            
            val sequenceMatch = Regex("<sequence\\s+([^>]+?)>", RegexOption.IGNORE_CASE).find(headerContent)
            if (sequenceMatch != null) {
                val sequenceAttributes = sequenceMatch.groupValues[1]
                val nameMatch = Regex("name\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(sequenceAttributes)
                if (nameMatch != null) {
                    series = nameMatch.groupValues[1].trim()
                }
                val indexMatch = Regex("number\\s*=\\s*[\"'](\\d+)[\"']", RegexOption.IGNORE_CASE).find(sequenceAttributes)
                if (indexMatch != null) {
                    seriesIndex = indexMatch.groupValues[1].toIntOrNull()
                }
            }
            
            val langMatch = Regex("<lang>\\s*([^<]+?)\\s*</lang>", RegexOption.IGNORE_CASE).find(headerContent)
            if (langMatch != null) {
                lang = langMatch.groupValues[1].trim()
            }
            
            val finalTitle = title.ifBlank { defaultTitle }
            val authorList = listOf(firstName, lastName).filter { it.isNotBlank() }
            val finalAuthor = if (authorList.isNotEmpty()) authorList.joinToString(" ") else "Unknown Author"
            
            val annotation = extractAnnotation(headerContent)
            
            // Return metadata with an empty content.
            BookMetadata(finalTitle, finalAuthor, "", series, seriesIndex, lang, annotation)
        } catch (e: Exception) {
            BookMetadata(defaultTitle, "Unknown Author", "", null, null, null, null)
        }
    }

    fun parse(fb2Content: String, defaultTitle: String): BookMetadata {
        val inputStream = fb2Content.byteInputStream(Charsets.UTF_8)
        val metadata = parse(inputStream, defaultTitle)
        return metadata.copy(content = fb2Content)
    }
}
