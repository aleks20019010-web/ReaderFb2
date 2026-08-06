package com.nightread.app.service

import android.util.Log
import com.nightread.app.service.BookParser.ParsedBook
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

data class Fb3ParsedBook(
    val title: String,
    val author: String,
    val content: String,
    val annotation: String? = null,
    val coverBytes: ByteArray? = null,
    val series: String? = null,
    val seriesIndex: Int? = null,
    val language: String? = null
)

object Fb3Parser : BookParser {
    private const val TAG = "Fb3Parser"

    fun isFb3(file: File): Boolean {
        val name = file.name.lowercase()
        if (name.endsWith(".fb3") || name.endsWith(".fb3.zip")) return true
        if (file.extension.lowercase() == "zip") {
            try {
                FileInputStream(file).use { fis ->
                    ZipInputStream(fis.buffered()).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val entryName = entry.name.lowercase()
                            if (!entry.isDirectory && (entryName.endsWith(".fb3") || entryName.endsWith("description.xml") || entryName == "fb3/description.xml")) {
                                return true
                            }
                            entry = zis.nextEntry
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return false
    }

    override fun parse(file: File, defaultTitle: String): ParsedBook {
        val parsed = parseFb3(file, defaultTitle)
        return ParsedBook(
            title = parsed.title,
            author = parsed.author,
            content = parsed.content,
            coverBytes = parsed.coverBytes,
            annotation = parsed.annotation
        )
    }

    fun parseFb3(file: File, defaultTitle: String, extractContent: Boolean = true): Fb3ParsedBook {
        return try {
            FileInputStream(file).use { fis ->
                parseStream(fis, defaultTitle, extractContent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing FB3 file: ${file.absolutePath}", e)
            Fb3ParsedBook(title = defaultTitle, author = "Неизвестен", content = "")
        }
    }

    fun parseBytes(bytes: ByteArray, defaultTitle: String, extractContent: Boolean = true): Fb3ParsedBook {
        return try {
            java.io.ByteArrayInputStream(bytes).use { bais ->
                parseStream(bais, defaultTitle, extractContent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing FB3 bytes", e)
            Fb3ParsedBook(title = defaultTitle, author = "Неизвестен", content = "")
        }
    }

    private fun parseStream(inputStream: InputStream, defaultTitle: String, extractContent: Boolean): Fb3ParsedBook {
        var descriptionXml: String? = null
        val bodyXmls = mutableListOf<String>()
        val zipEntriesMap = mutableMapOf<String, ByteArray>()
        var nestedFb3Bytes: ByteArray? = null

        ZipInputStream(inputStream.buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name.lowercase()
                if (!entry.isDirectory) {
                    if (name.endsWith(".fb3")) {
                        nestedFb3Bytes = readEntryBytes(zis)
                    } else if (name.endsWith("description.xml") || name == "fb3/description.xml") {
                        val bytes = readEntryBytes(zis)
                        descriptionXml = String(bytes, Charsets.UTF_8)
                    } else if (extractContent && (name.endsWith("body.xml") || (name.contains("body") && name.endsWith(".xml")))) {
                        val bytes = readEntryBytes(zis)
                        bodyXmls.add(String(bytes, Charsets.UTF_8))
                    } else if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".xml")) {
                        val bytes = readEntryBytes(zis)
                        zipEntriesMap[entry.name] = bytes
                        zipEntriesMap[name] = bytes
                    }
                }
                entry = zis.nextEntry
            }
        }

        if (nestedFb3Bytes != null && descriptionXml == null) {
            return parseBytes(nestedFb3Bytes!!, defaultTitle)
        }

        // Extract metadata from description.xml
        var title = defaultTitle
        var author = "Неизвестен"
        var annotation: String? = null
        var series: String? = null
        var seriesIndex: Int? = null
        var language: String? = null
        var coverPathInZip: String? = null

        if (descriptionXml != null) {
            val desc = descriptionXml!!

            // Title extraction
            val titleMainMatch = Regex("<title[^>]*>\\s*<main>\\s*([^<]+?)\\s*</main>", RegexOption.IGNORE_CASE).find(desc)
                ?: Regex("<book-title>\\s*([^<]+?)\\s*</book-title>", RegexOption.IGNORE_CASE).find(desc)
                ?: Regex("<title>\\s*([^<]+?)\\s*</title>", RegexOption.IGNORE_CASE).find(desc)
            if (titleMainMatch != null) {
                val extractedTitle = unescapeXml(titleMainMatch.groupValues[1].trim())
                if (extractedTitle.isNotBlank()) {
                    title = extractedTitle
                }
            }

            // Authors extraction
            val authorMatches = Regex("<author[^>]*>\\s*(.*?)\\s*</author>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).findAll(desc).toList()
            val authorList = mutableListOf<String>()
            for (authMatch in authorMatches) {
                val authBlock = authMatch.groupValues[1]
                val fn = Regex("<first-name>\\s*([^<]+?)\\s*</first-name>", RegexOption.IGNORE_CASE).find(authBlock)?.groupValues?.get(1)?.trim()
                val mn = Regex("<middle-name>\\s*([^<]+?)\\s*</middle-name>", RegexOption.IGNORE_CASE).find(authBlock)?.groupValues?.get(1)?.trim()
                val ln = Regex("<last-name>\\s*([^<]+?)\\s*</last-name>", RegexOption.IGNORE_CASE).find(authBlock)?.groupValues?.get(1)?.trim()
                val fullName = listOfNotNull(fn, mn, ln).joinToString(" ").trim()
                if (fullName.isNotBlank()) {
                    authorList.add(unescapeXml(fullName))
                }
            }
            if (authorList.isNotEmpty()) {
                author = authorList.joinToString(", ")
            }

            // Sequence / Series extraction
            val seqMatch = Regex("<sequence[^>]+>", RegexOption.IGNORE_CASE).find(desc)
            if (seqMatch != null) {
                val attrs = seqMatch.groupValues[0]
                val nameMatch = Regex("name\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(attrs)
                val numMatch = Regex("number\\s*=\\s*[\"'](\\d+)[\"']", RegexOption.IGNORE_CASE).find(attrs)
                if (nameMatch != null) series = unescapeXml(nameMatch.groupValues[1].trim())
                if (numMatch != null) seriesIndex = numMatch.groupValues[1].toIntOrNull()
            }

            // Language
            val langMatch = Regex("<lang>\\s*([^<]+?)\\s*</lang>", RegexOption.IGNORE_CASE).find(desc)
                ?: Regex("<language>\\s*([^<]+?)\\s*</language>", RegexOption.IGNORE_CASE).find(desc)
            if (langMatch != null) {
                language = langMatch.groupValues[1].trim()
            }

            // Annotation
            val annotMatch = Regex("<annotation[^>]*>\\s*(.*?)\\s*</annotation>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).find(desc)
            if (annotMatch != null) {
                val rawAnnot = annotMatch.groupValues[1]
                annotation = stripTags(unescapeXml(rawAnnot)).trim()
            }

            // Cover path inside ZIP
            val coverMatch = Regex("<cover[^>]*>\\s*<image[^>]+(?:src|href)=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(desc)
                ?: Regex("<image[^>]+(?:src|href)=[\"']([^\"']+)[\"'][^>]*class=[\"'][^\"']*cover[^\"']*[\"']", RegexOption.IGNORE_CASE).find(desc)
            if (coverMatch != null) {
                coverPathInZip = coverMatch.groupValues[1]
            }
        }

        // Cover bytes extraction
        var coverBytes: ByteArray? = null
        if (!coverPathInZip.isNullOrBlank()) {
            val key = coverPathInZip!!
            coverBytes = zipEntriesMap[key] ?: zipEntriesMap[key.lowercase()] ?: zipEntriesMap[key.removePrefix("fb3/")]
        }
        if (coverBytes == null) {
            coverBytes = zipEntriesMap.entries.firstOrNull { (k, _) ->
                val lk = k.lowercase()
                lk.contains("cover") && (lk.endsWith(".jpg") || lk.endsWith(".jpeg") || lk.endsWith(".png"))
            }?.value
        }

        // Combine body content into HTML string
        val rawBody = if (bodyXmls.isNotEmpty()) {
            bodyXmls.joinToString("\n")
        } else {
            ""
        }

        val formattedContent = formatFb3BodyToHtml(rawBody)

        return Fb3ParsedBook(
            title = title,
            author = author,
            content = formattedContent,
            annotation = annotation,
            coverBytes = coverBytes,
            series = series,
            seriesIndex = seriesIndex,
            language = language ?: "ru"
        )
    }

    private fun readEntryBytes(zis: ZipInputStream): ByteArray {
        val bos = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var read: Int
        while (zis.read(buffer, 0, buffer.size).also { read = it } != -1) {
            bos.write(buffer, 0, read)
        }
        return bos.toByteArray()
    }

    private fun formatFb3BodyToHtml(bodyXml: String): String {
        if (bodyXml.isBlank()) return "<p>Содержимое книги пусто.</p>"

        var html = bodyXml
            .replace(Regex("<fb3-body[^>]*>", RegexOption.IGNORE_CASE), "<div>")
            .replace(Regex("</fb3-body>", RegexOption.IGNORE_CASE), "</div>")
            .replace(Regex("<section[^>]*>", RegexOption.IGNORE_CASE), "<section>")
            .replace(Regex("</section>", RegexOption.IGNORE_CASE), "</section>")
            .replace(Regex("<title[^>]*>", RegexOption.IGNORE_CASE), "<h2>")
            .replace(Regex("</title>", RegexOption.IGNORE_CASE), "</h2>")
            .replace(Regex("<subtitle[^>]*>", RegexOption.IGNORE_CASE), "<h3>")
            .replace(Regex("</subtitle>", RegexOption.IGNORE_CASE), "</h3>")

        if (!html.contains("<p") && !html.contains("<div")) {
            html = html.split("\n")
                .filter { it.isNotBlank() }
                .joinToString("\n") { "<p>${it.trim()}</p>" }
        }

        return html
    }

    private fun stripTags(xml: String): String {
        return xml.replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun unescapeXml(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#160;", " ")
            .replace("&nbsp;", " ")
    }
}
