package com.nightread.app.service

import android.util.Log
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile

object EpubParser : BookParser {
    private const val TAG = "EpubParser"

    data class EpubMetadata(
        val title: String,
        val author: String,
        val content: String,
        val language: String?,
        val annotation: String?,
        val coverBytes: ByteArray?,
        val notes: Map<String, String> = emptyMap()
    )

    override fun parse(file: File, defaultTitle: String): BookParser.ParsedBook {
        val metadata = parseEpub(file, defaultTitle)
        return BookParser.ParsedBook(
            title = metadata.title,
            author = metadata.author,
            content = metadata.content,
            notes = metadata.notes,
            coverBytes = metadata.coverBytes
        )
    }

    fun parseEpub(file: File, defaultTitle: String): EpubMetadata {
        var zipFile: ZipFile? = null
        try {
            zipFile = ZipFile(file)
            
            // 1. Find the .opf file path from META-INF/container.xml
            val containerEntry = findZipEntry(zipFile, "META-INF/container.xml")
                ?: throw Exception("META-INF/container.xml not found")
            val containerContent = zipFile.getInputStream(containerEntry).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            
            val containerClean = containerContent.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
            val rootfileTags = extractTags(containerClean, "rootfile")
            val opfPath = rootfileTags.firstOrNull()?.get("full-path")
                ?: throw Exception("OPF path not found in container.xml")
            
            // 2. Read content.opf
            val opfEntry = findZipEntry(zipFile, opfPath)
                ?: throw Exception("OPF file not found at path: $opfPath")
            val opfContent = zipFile.getInputStream(opfEntry).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            
            val opfContentClean = opfContent.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
            val opfDir = if (opfPath.contains("/")) opfPath.substringBeforeLast("/") + "/" else ""
            
            // Parse metadata using robust extraction
            val title = extractElementText(opfContentClean, "dc:title") ?: defaultTitle
            val author = extractElementText(opfContentClean, "dc:creator") ?: "Неизвестен"
            
            var annotation = extractElementText(opfContentClean, "dc:description")
            if (annotation != null) {
                annotation = annotation.replace(Regex("<[^>]+>"), " ").trim()
            }
            
            val language = extractElementText(opfContentClean, "dc:language") ?: "ru"
            
            // 3. Parse Manifest: map item IDs to hrefs
            val manifestItems = mutableMapOf<String, String>() // id -> href
            val itemTags = extractTags(opfContentClean, "item")
            for (attrs in itemTags) {
                val id = attrs["id"]
                val href = attrs["href"]
                if (id != null && href != null) {
                    val decodedHref = try {
                        java.net.URLDecoder.decode(href, "UTF-8")
                    } catch (e: Exception) {
                        href
                    }
                    manifestItems[id] = decodedHref
                }
            }
            
            // 4. Parse Spine: reading order of items
            val spineIds = mutableListOf<String>()
            val itemrefTags = extractTags(opfContentClean, "itemref")
            for (attrs in itemrefTags) {
                val idref = attrs["idref"]
                if (idref != null) {
                    spineIds.add(idref)
                }
            }
            
            // 5. Read all spine HTML chapters and parse them to plain text with chapter markers
            val textBuilder = StringBuilder()
            val notesMap = mutableMapOf<String, String>()
            
            for (idref in spineIds) {
                // Support case-insensitive key matching in manifestItems fallback
                val href = manifestItems[idref] 
                    ?: manifestItems.entries.firstOrNull { it.key.equals(idref, ignoreCase = true) }?.value
                    ?: continue
                
                val zipPath = opfDir + href
                val entry = findZipEntry(zipFile, zipPath) ?: continue
                
                val htmlContent = zipFile.getInputStream(entry).use { stream ->
                    stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                }
                
                val pageText = parseHtmlToText(htmlContent, notesMap)
                if (pageText.isNotBlank()) {
                    if (textBuilder.isNotEmpty()) {
                        textBuilder.append("\n")
                    }
                    textBuilder.append(pageText)
                }
            }
            
            // 6. Find cover image
            var coverBytes: ByteArray? = null
            var coverHref = ""
            
            // Check for cover image properties (EPUB 3)
            val epub3Item = itemTags.firstOrNull { 
                it["properties"]?.contains("cover-image", ignoreCase = true) == true 
            }
            if (epub3Item != null) {
                coverHref = epub3Item["href"] ?: ""
            }
            
            if (coverHref.isBlank()) {
                // Try EPUB 2 meta name="cover"
                val metaTags = extractTags(opfContentClean, "meta")
                val epub2Meta = metaTags.firstOrNull { 
                    it["name"]?.equals("cover", ignoreCase = true) == true 
                }
                if (epub2Meta != null) {
                    val coverId = epub2Meta["content"] ?: ""
                    coverHref = manifestItems[coverId] 
                        ?: manifestItems.entries.firstOrNull { it.key.equals(coverId, ignoreCase = true) }?.value
                        ?: ""
                }
            }
            
            // Additional fallback: search manifest item IDs or hrefs containing "cover"
            if (coverHref.isBlank()) {
                for ((id, href) in manifestItems) {
                    if (id.contains("cover", ignoreCase = true) || href.contains("cover", ignoreCase = true)) {
                        coverHref = href
                        break
                    }
                }
            }
            
            if (coverHref.isNotBlank()) {
                val zipPath = opfDir + coverHref
                val entry = findZipEntry(zipFile, zipPath)
                if (entry != null) {
                    coverBytes = zipFile.getInputStream(entry).use { it.readBytes() }
                }
            }
            
            return EpubMetadata(
                title = title,
                author = author,
                content = textBuilder.toString(),
                language = language,
                annotation = annotation,
                coverBytes = coverBytes,
                notes = notesMap
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing EPUB: ${file.absolutePath}", e)
            return EpubMetadata(defaultTitle, "Неизвестен", "", null, null, null)
        } finally {
            try {
                zipFile?.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun findZipEntry(zipFile: ZipFile, path: String): java.util.zip.ZipEntry? {
        val exactPath = normalizePath(path)
        val entry = zipFile.getEntry(exactPath)
        if (entry != null) return entry

        val normalizedSlash = exactPath.replace("\\", "/")
        val entrySlash = zipFile.getEntry(normalizedSlash)
        if (entrySlash != null) return entrySlash

        val pathLower = normalizedSlash.lowercase()
        val entries = zipFile.entries()
        while (entries.hasMoreElements()) {
            val e = entries.nextElement()
            if (e.name.replace("\\", "/").lowercase() == pathLower) {
                return e
            }
        }
        return null
    }

    private fun normalizePath(path: String): String {
        val parts = path.split("/")
        val resolved = mutableListOf<String>()
        for (part in parts) {
            if (part == "." || part.isEmpty()) continue
            if (part == "..") {
                if (resolved.isNotEmpty()) {
                    resolved.removeAt(resolved.size - 1)
                }
            } else {
                resolved.add(part)
            }
        }
        return resolved.joinToString("/")
    }

    private fun extractTags(xml: String, tagName: String): List<Map<String, String>> {
        val result = mutableListOf<Map<String, String>>()
        val tagLower = tagName.lowercase()
        val xmlLower = xml.lowercase()
        var pos = 0
        while (pos < xml.length) {
            val startIdx = xmlLower.indexOf("<$tagLower", pos)
            if (startIdx == -1) break
            
            // Check if it's a full word match to prevent "<itemref" from matching "<item"
            val nextCharPos = startIdx + 1 + tagLower.length
            if (nextCharPos < xml.length) {
                val nextChar = xmlLower[nextCharPos]
                if (nextChar != ' ' && nextChar != '\t' && nextChar != '\r' && nextChar != '\n' && nextChar != '/' && nextChar != '>') {
                    pos = nextCharPos
                    continue
                }
            }
            
            val endIdx = xml.indexOf(">", startIdx)
            if (endIdx == -1) break
            
            val tagContent = xml.substring(startIdx, endIdx + 1)
            result.add(parseAttributes(tagContent))
            pos = endIdx + 1
        }
        return result
    }

    private fun parseAttributes(tagContent: String): Map<String, String> {
        val attributes = mutableMapOf<String, String>()
        val regex = Regex("""([a-zA-Z0-9_:.-]+)\s*=\s*["']([^"']*)["']""")
        for (match in regex.findAll(tagContent)) {
            val name = match.groupValues[1].lowercase()
            val value = match.groupValues[2]
            attributes[name] = value
        }
        return attributes
    }

    private fun extractElementText(xml: String, tagName: String): String? {
        val tagLower = tagName.lowercase()
        val xmlLower = xml.lowercase()
        
        val startTagIdx = xmlLower.indexOf("<$tagLower")
        if (startTagIdx == -1) return null
        
        val openTagEndIdx = xml.indexOf(">", startTagIdx)
        if (openTagEndIdx == -1) return null
        
        val endTagIdx = xmlLower.indexOf("</$tagLower>", openTagEndIdx)
        if (endTagIdx == -1) return null
        
        val innerContent = xml.substring(openTagEndIdx + 1, endTagIdx)
        return decodeHtmlEntities(innerContent.replace(Regex("<[^>]+>"), "")).trim()
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

    private fun extractNotesFromHtml(html: String, notesMap: MutableMap<String, String>) {
        val regex = Regex("""<([a-zA-Z0-9]+)[^>]*id=["']([^"']+)["'][^>]*>(.*?)</\1>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        for (match in regex.findAll(html)) {
            val id = match.groupValues[2]
            var innerText = match.groupValues[3]
            innerText = innerText.replace(Regex("<[^>]+>"), " ")
            innerText = decodeHtmlEntities(innerText).trim()
            if (innerText.isNotEmpty()) {
                notesMap[id] = innerText
            }
        }
    }

    private fun parseHtmlToText(html: String, notesMap: MutableMap<String, String>): String {
        try {
            extractNotesFromHtml(html, notesMap)

            var cleanHtml = html
            val headStart = cleanHtml.indexOf("<head", ignoreCase = true)
            val headEnd = cleanHtml.indexOf("</head>", ignoreCase = true)
            if (headStart != -1 && headEnd != -1 && headEnd > headStart) {
                cleanHtml = cleanHtml.removeRange(headStart, headEnd + "</head>".length)
            }
            
            cleanHtml = cleanHtml.replace(Regex("<style[^>]*>.*?</style>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
            cleanHtml = cleanHtml.replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
            
            cleanHtml = cleanHtml
                .replace(Regex("<blockquote[^>]*>", RegexOption.IGNORE_CASE), "\n[CITE]")
                .replace(Regex("</blockquote>", RegexOption.IGNORE_CASE), "[/CITE]\n")
                .replace(Regex("<cite[^>]*>", RegexOption.IGNORE_CASE), "\n[CITE]")
                .replace(Regex("</cite>", RegexOption.IGNORE_CASE), "[/CITE]\n")
                .replace(Regex("<sup[^>]*>", RegexOption.IGNORE_CASE), "[SUP]")
                .replace(Regex("</sup>", RegexOption.IGNORE_CASE), "[/SUP]")

            // Format note links: <a href="...#note_id">1</a>
            val noteLinkRegex = Regex("""<a[^>]*(?:href|l:href)=["']([^"']*#)([^"']+)["'][^>]*>(.*?)</a>""", RegexOption.IGNORE_CASE)
            cleanHtml = noteLinkRegex.replace(cleanHtml) { matchResult ->
                val noteId = matchResult.groupValues[2]
                val linkText = matchResult.groupValues[3].replace(Regex("<[^>]+>"), "")
                "[NOTE:$noteId]$linkText[/NOTE]"
            }

            cleanHtml = cleanHtml
                .replace(Regex("<h[1-3][^>]*>", RegexOption.IGNORE_CASE), "\n\u000C[CHAPTER]")
                .replace(Regex("</h[1-3]>", RegexOption.IGNORE_CASE), "[/CHAPTER]\n")
                .replace(Regex("<title[^>]*>", RegexOption.IGNORE_CASE), "\n\u000C[CHAPTER]")
                .replace(Regex("</title>", RegexOption.IGNORE_CASE), "[/CHAPTER]\n")
            
            cleanHtml = cleanHtml
                .replace(Regex("<p[^>]*>", RegexOption.IGNORE_CASE), "\n\u00A0\u00A0\u00A0\u00A0")
                .replace(Regex("</p>", RegexOption.IGNORE_CASE), "")
                .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
                .replace(Regex("<li[^>]*>", RegexOption.IGNORE_CASE), "\n\u00A0\u00A0- ")
                .replace(Regex("</li>", RegexOption.IGNORE_CASE), "")
            
            var text = cleanHtml.replace(Regex("<[^>]+>"), "")
            
            text = decodeHtmlEntities(text)
            
            text = text.replace(Regex("([ \\t\\r]*\\n[ \\t\\r]*){2,}"), "\n")
            text = text.replace(Regex("\\n[ \\t\\r]+(?=\\u00A0)"), "\n")
            text = text.replace(Regex("\\u000C+"), "\u000C")
            
            return text.trim().trim('\u000C').trim()
        } catch (e: Exception) {
            Log.e(TAG, "Error formatting HTML to text", e)
            return html
        }
    }

    fun extractText(file: File): String {
        return parseEpub(file, file.nameWithoutExtension).content
    }
}
