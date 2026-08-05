package com.nightread.app.data

import android.util.Log
import java.io.File
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

data class EpubMetadata(
    val identifier: String,
    val title: String,
    val author: String,
    val content: String,
    val coverPath: String? = null,
    val description: String? = null,
    val opfDir: String = ""
)

object EpubIdentifierHelper {
    private const val TAG = "EpubIdentifierHelper"

    fun isEpub(file: File): Boolean {
        if (file.extension.lowercase() == "epub") return true
        return try {
            ZipInputStream(file.inputStream().buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val normalizedName = entry.name.replace("\\", "/").trim().lowercase()
                    if (normalizedName == "mimetype") {
                        val content = zip.readBytes().toString(Charsets.UTF_8).trim()
                        return content.contains("application/epub+zip")
                    }
                    if (normalizedName == "meta-inf/container.xml" || normalizedName.endsWith(".opf")) {
                        return true
                    }
                    entry = zip.nextEntry
                }
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if file is EPUB: ${file.name}", e)
            false
        }
    }

    fun cleanZipPath(path: String): String {
        var p = path.replace("\\", "/").replace("//", "/").trim().lowercase()
        while (p.startsWith("./")) {
            p = p.substring(2)
        }
        while (p.startsWith("/")) {
            p = p.substring(1)
        }
        return p
    }

    private fun resolvePath(baseDir: String, relativePath: String): String {
        val decoded = try { java.net.URLDecoder.decode(relativePath, "UTF-8") } catch (e: Exception) { relativePath }
        val cleanRelative = cleanZipPath(decoded)
        val cleanBase = cleanZipPath(baseDir)

        if (cleanBase.isEmpty()) return cleanRelative
        if (cleanRelative.startsWith("$cleanBase/")) return cleanRelative

        val segments = mutableListOf<String>()
        if (cleanBase.isNotEmpty()) {
            segments.addAll(cleanBase.split("/"))
        }
        for (part in cleanRelative.split("/")) {
            if (part == "..") {
                if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex)
            } else if (part != "." && part.isNotEmpty()) {
                segments.add(part)
            }
        }
        return segments.joinToString("/")
    }

    fun cleanHtmlAndEntities(rawText: String?): String? {
        if (rawText.isNullOrBlank()) return null
        var text = rawText.trim()

        // Unwrap CDATA if present
        val cdataMatch = Regex("<!\\[CDATA\\[(.*?)\\]\\]>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).find(text)
        if (cdataMatch != null) {
            text = cdataMatch.groupValues[1]
        }

        // Convert break/paragraph tags to newlines
        text = text.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</p\\s*>", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("</div>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<p[^>]*>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<div[^>]*>", RegexOption.IGNORE_CASE), "")

        // Strip remaining XML/HTML tags
        text = text.replace(Regex("<[^>]+>"), "")

        // Unescape common HTML entities
        text = text.replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace("&copy;", "©")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&laquo;", "«")
            .replace("&raquo;", "»")

        // Unescape numeric entities
        text = text.replace(Regex("&#(\\d+);")) { match ->
            try {
                val code = match.groupValues[1].toInt()
                code.toChar().toString()
            } catch (e: Exception) {
                " "
            }
        }

        // Clean up whitespace and newlines
        text = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n\n")

        return if (text.isBlank()) null else text
    }

    fun computeFileSha1(file: File): String? {
        if (!file.exists() || !file.isFile) return null
        return try {
            java.io.FileInputStream(file).use { fis ->
                val digest = java.security.MessageDigest.getInstance("SHA-1")
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
                val sha1Bytes = digest.digest()
                sha1Bytes.joinToString("") { "%02x".format(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compute SHA-1 of local file: ${file.absolutePath}", e)
            null
        }
    }

    fun getEpubMetadata(file: File): EpubMetadata? {
        return try {
            var opfPath: String? = null
            var opfContent: String? = null
            val zipFiles = mutableMapOf<String, ByteArray>()
            var coverPath: String? = null

            ZipInputStream(file.inputStream().buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val normalizedName = cleanZipPath(entry.name)
                    val ext = normalizedName.substringAfterLast(".", "").lowercase()
                    // Only store opf, xml, container, and meta files to save memory
                    if (ext == "opf" || ext == "xml" || normalizedName.contains("meta-inf") ||
                        normalizedName.contains("annotation") || normalizedName.contains("description")) {
                        val content = zip.readBytes()
                        if (normalizedName == "meta-inf/container.xml") {
                            val strContent = content.toString(Charsets.UTF_8)
                            val match = Regex("<rootfile\\s+[^>]*full-path\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(strContent)
                            opfPath = match?.groupValues?.get(1)
                        }
                        zipFiles[normalizedName] = content
                    }
                    entry = zip.nextEntry
                }
            }

            if (opfPath == null) {
                opfPath = zipFiles.keys.firstOrNull { it.endsWith(".opf") }
            }

            if (opfPath != null) {
                val decodedOpf = try { java.net.URLDecoder.decode(opfPath!!, "UTF-8") } catch (e: Exception) { opfPath!! }
                val normalizedOpfPath = cleanZipPath(decodedOpf)
                val opfDir = if (normalizedOpfPath.contains("/")) normalizedOpfPath.substringBeforeLast("/") else ""
                opfContent = zipFiles[normalizedOpfPath]?.toString(Charsets.UTF_8)
                    ?: zipFiles[cleanZipPath(opfPath!!)]?.toString(Charsets.UTF_8)
                    ?: zipFiles.values.firstOrNull { it.isNotEmpty() && (it.size > 50) && String(it.take(100).toByteArray()).contains("<package", ignoreCase = true) }?.toString(Charsets.UTF_8)

                if (opfContent != null) {
                    val idMatch = Regex("<(?:\\w+:)?identifier[^>]*>([^<]+)</(?:\\w+:)?identifier>", RegexOption.IGNORE_CASE).find(opfContent)
                    val titleMatch = Regex("<(?:\\w+:)?title[^>]*>([^<]+)</(?:\\w+:)?title>", RegexOption.IGNORE_CASE).find(opfContent)
                    val authorMatch = Regex("<(?:\\w+:)?creator[^>]*>([^<]+)</(?:\\w+:)?creator>", RegexOption.IGNORE_CASE).find(opfContent)

                    val titleText = cleanHtmlAndEntities(titleMatch?.groupValues?.get(1)) ?: "Unknown"
                    val authorText = cleanHtmlAndEntities(authorMatch?.groupValues?.get(1)) ?: "Unknown"

                    // --- ANNOTATION / DESCRIPTION EXTRACTION ---
                    var descriptionText: String? = null

                    // 1. Try <dc:description> tag with DOT_MATCHES_ALL
                    val descMatch = Regex("<(?:\\w+:)?description[^>]*>(.*?)</(?:\\w+:)?description>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).find(opfContent)
                    if (descMatch != null) {
                        descriptionText = cleanHtmlAndEntities(descMatch.groupValues[1])
                    }

                    // 2. Try <meta name="description" content="..."/>
                    if (descriptionText.isNullOrBlank()) {
                        val metaDescMatches = Regex("<meta\\s+([^>]+)>", RegexOption.IGNORE_CASE).findAll(opfContent)
                        for (meta in metaDescMatches) {
                            val attrs = meta.groupValues[1]
                            val isDesc = Regex("(name|property)\\s*=\\s*[\"'][^\"']*description[\"']", RegexOption.IGNORE_CASE).containsMatchIn(attrs)
                            if (isDesc) {
                                val contentM = Regex("content\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(attrs)
                                if (contentM != null) {
                                    descriptionText = cleanHtmlAndEntities(contentM.groupValues[1])
                                    if (!descriptionText.isNullOrBlank()) break
                                }
                            }
                        }
                    }

                    // 3. Try checking dedicated description/annotation file in zip
                    if (descriptionText.isNullOrBlank()) {
                        val descFileKey = zipFiles.keys.firstOrNull { key ->
                            val name = key.lowercase()
                            (name.contains("annotation") || name.contains("description") || name.contains("summary") || name.contains("about")) &&
                                    (name.endsWith(".xhtml") || name.endsWith(".html") || name.endsWith(".htm") || name.endsWith(".txt"))
                        }
                        if (descFileKey != null) {
                            val bytes = zipFiles[descFileKey]
                            if (bytes != null && bytes.isNotEmpty()) {
                                val strContent = String(bytes, Charsets.UTF_8)
                                descriptionText = cleanHtmlAndEntities(strContent)
                            }
                        }
                    }

                    // --- COVER IMAGE IDENTIFICATION ---
                    var coverIdOrPath: String? = null

                    // EPUB 2 <meta name="cover" content="..."/>
                    val metaMatches = Regex("<meta\\s+([^>]+)>", RegexOption.IGNORE_CASE).findAll(opfContent)
                    for (meta in metaMatches) {
                        val attrs = meta.groupValues[1]
                        val isCoverName = Regex("(name|property)\\s*=\\s*[\"'](?:cover|cover-image|calibre:cover|other\\.ms-coverimage-standard)[\"']", RegexOption.IGNORE_CASE).containsMatchIn(attrs)
                        if (isCoverName) {
                            val contentMatch = Regex("content\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(attrs)
                            if (contentMatch != null) {
                                coverIdOrPath = contentMatch.groupValues[1]
                                break
                            }
                        }
                    }

                    // Parse manifest items
                    val manifestMap = mutableMapOf<String, String>() // id -> href
                    val manifestMediaTypeMap = mutableMapOf<String, String>() // href -> media-type
                    var epub3CoverHref: String? = null

                    val itemMatches = Regex("<item\\s+([^>]+)>", RegexOption.IGNORE_CASE).findAll(opfContent)
                    for (item in itemMatches) {
                        val attrs = item.groupValues[1]
                        val idM = Regex("id\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(attrs)
                        val hrefM = Regex("href\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(attrs)
                        val mediaTypeM = Regex("media-type\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(attrs)
                        val propertiesM = Regex("properties\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(attrs)

                        val id = idM?.groupValues?.get(1) ?: ""
                        val href = hrefM?.groupValues?.get(1) ?: ""
                        val mediaType = mediaTypeM?.groupValues?.get(1) ?: ""
                        val properties = propertiesM?.groupValues?.get(1) ?: ""

                        if (id.isNotEmpty() && href.isNotEmpty()) {
                            manifestMap[id] = href
                        }
                        if (href.isNotEmpty()) {
                            manifestMediaTypeMap[href] = mediaType
                        }

                        // EPUB 3 properties="cover-image"
                        if (properties.contains("cover-image", ignoreCase = true) && href.isNotEmpty()) {
                            epub3CoverHref = href
                        }
                    }

                    var rawCoverPath: String? = epub3CoverHref

                    if (rawCoverPath == null && coverIdOrPath != null) {
                        if (manifestMap.containsKey(coverIdOrPath)) {
                            rawCoverPath = manifestMap[coverIdOrPath]
                        } else if (coverIdOrPath.contains(".") || coverIdOrPath.contains("/")) {
                            rawCoverPath = coverIdOrPath
                        }
                    }

                    // Check <guide> section
                    if (rawCoverPath == null) {
                        val guideMatch = Regex("<guide[^>]*>(.*?)</guide>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).find(opfContent)
                        if (guideMatch != null) {
                            val refMatches = Regex("<reference\\s+([^>]+)>", RegexOption.IGNORE_CASE).findAll(guideMatch.groupValues[1])
                            for (ref in refMatches) {
                                val attrs = ref.groupValues[1]
                                val typeM = Regex("type\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(attrs)
                                val hrefM = Regex("href\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(attrs)
                                if (typeM != null && hrefM != null) {
                                    val type = typeM.groupValues[1].lowercase()
                                    if (type == "cover" || type.contains("cover")) {
                                        rawCoverPath = hrefM.groupValues[1]
                                        break
                                    }
                                }
                            }
                        }
                    }

                    // Check manifest IDs with cover keywords
                    if (rawCoverPath == null) {
                        val coverKeys = listOf("cover", "cover-image", "coverimage", "cover_image", "title-page", "titlepage", "thumb", "thumbnail")
                        for (key in coverKeys) {
                            if (manifestMap.containsKey(key)) {
                                rawCoverPath = manifestMap[key]
                                break
                            }
                        }
                    }

                    // Check manifest hrefs with cover keywords
                    if (rawCoverPath == null) {
                        for ((id, href) in manifestMap) {
                            val mediaType = manifestMediaTypeMap[href] ?: ""
                            val lowerHref = href.lowercase()
                            val lowerId = id.lowercase()
                            if (mediaType.startsWith("image/", ignoreCase = true) ||
                                lowerHref.endsWith(".jpg") || lowerHref.endsWith(".jpeg") ||
                                lowerHref.endsWith(".png") || lowerHref.endsWith(".webp") || lowerHref.endsWith(".gif")) {
                                if (lowerHref.contains("cover") || lowerId.contains("cover") ||
                                    lowerHref.contains("front") || lowerId.contains("front") ||
                                    lowerHref.contains("title") || lowerId.contains("title")) {
                                    rawCoverPath = href
                                    break
                                }
                            }
                        }
                    }

                    if (rawCoverPath != null) {
                        coverPath = resolvePath(opfDir, rawCoverPath)
                    }

                    val identifier = computeFileSha1(file) ?: idMatch?.groupValues?.get(1)?.trim() ?: java.util.UUID.randomUUID().toString()

                    // Parse Spine to construct content
                    val spineMatch = Regex("<spine[^>]*>(.*?)</spine>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).find(opfContent)
                    val spine = spineMatch?.groupValues?.get(1) ?: ""
                    val itemrefMatches = Regex("<itemref\\s+([^>]+)>", RegexOption.IGNORE_CASE).findAll(spine)
                    val idrefs = mutableListOf<String>()
                    for (itemref in itemrefMatches) {
                        val attrs = itemref.groupValues[1]
                        val idrefM = Regex("idref\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(attrs)
                        if (idrefM != null) {
                            idrefs.add(idrefM.groupValues[1])
                        }
                    }

                    val contentBuilder = StringBuilder()
                    for (idref in idrefs) {
                        val href = manifestMap[idref]
                        if (href != null) {
                            val fullPath = resolvePath(opfDir, href)
                            var bytes = zipFiles[fullPath]
                            if (bytes == null) {
                                bytes = zipFiles[cleanZipPath(href)]
                            }
                            if (bytes != null) {
                                val xhtmlContent = try {
                                    val strUtf8 = String(bytes, Charsets.UTF_8)
                                    if (!strUtf8.contains("\uFFFD")) {
                                        strUtf8
                                    } else {
                                        String(bytes, Charset.forName("windows-1251"))
                                    }
                                } catch (e: Exception) {
                                    String(bytes, Charset.forName("windows-1251"))
                                }

                                val bodyMatch = Regex("<body[^>]*>(.*?)</body>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).find(xhtmlContent)
                                if (bodyMatch != null) {
                                    contentBuilder.append(bodyMatch.groupValues[1])
                                    contentBuilder.append("\n\n")
                                }
                            }
                        }
                    }

                    if (contentBuilder.isEmpty()) {
                        val htmlFiles = zipFiles.keys.filter { it.endsWith(".xhtml") || it.endsWith(".html") || it.endsWith(".htm") }.sorted()
                        for (path in htmlFiles) {
                            val bytes = zipFiles[path]
                            if (bytes != null) {
                                val xhtmlContent = try {
                                    val strUtf8 = String(bytes, Charsets.UTF_8)
                                    if (!strUtf8.contains("\uFFFD")) {
                                        strUtf8
                                    } else {
                                        String(bytes, Charset.forName("windows-1251"))
                                    }
                                } catch (e: Exception) {
                                    String(bytes, Charset.forName("windows-1251"))
                                }
                                val bodyMatch = Regex("<body[^>]*>(.*?)</body>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).find(xhtmlContent)
                                if (bodyMatch != null) {
                                    contentBuilder.append(bodyMatch.groupValues[1])
                                } else {
                                    contentBuilder.append(xhtmlContent.replace(Regex("<[^>]+>"), " "))
                                }
                                contentBuilder.append("\n\n")
                            }
                        }
                    }

                    if (contentBuilder.isEmpty()) {
                        contentBuilder.append("Книга успешно импортирована.")
                    }

                    // Fallback description from content if still blank
                    if (descriptionText.isNullOrBlank() && contentBuilder.isNotBlank()) {
                        val excerpt = cleanHtmlAndEntities(contentBuilder.toString())
                        if (!excerpt.isNullOrBlank()) {
                            descriptionText = if (excerpt.length > 400) {
                                excerpt.take(400).trim() + "..."
                            } else {
                                excerpt
                            }
                        }
                    }

                    return EpubMetadata(
                        identifier = identifier,
                        title = titleText,
                        author = authorText,
                        content = contentBuilder.toString(),
                        coverPath = coverPath,
                        description = descriptionText,
                        opfDir = opfDir
                    )
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting EPUB metadata: ${file.name}", e)
            null
        }
    }

    fun unzip(zipFile: File, targetDirectory: File) {
        if (targetDirectory.exists() && targetDirectory.list()?.isNotEmpty() == true) {
            return
        }
        targetDirectory.mkdirs()
        try {
            ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val file = File(targetDirectory, entry.name)
                    if (entry.isDirectory) {
                        file.mkdirs()
                    } else {
                        file.parentFile?.mkdirs()
                        file.outputStream().use { output ->
                            zip.copyTo(output)
                        }
                    }
                    entry = zip.nextEntry
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unzipping file: ${zipFile.name}", e)
        }
    }

    fun extractAndSaveEpubCover(file: File, coverPath: String?, sha1: String, context: android.content.Context): String? {
        return try {
            val zipEntriesMap = mutableMapOf<String, ByteArray>()
            ZipInputStream(file.inputStream().buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = cleanZipPath(entry.name)
                        val ext = name.substringAfterLast(".", "").lowercase()
                        if (ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "xhtml", "html", "htm") || name.contains("cover")) {
                            if (entry.size < 10 * 1024 * 1024) {
                                zipEntriesMap[name] = zip.readBytes()
                            }
                        }
                    }
                    entry = zip.nextEntry
                }
            }

            var coverBytes: ByteArray? = null

            if (!coverPath.isNullOrEmpty()) {
                var targetPath = cleanZipPath(coverPath)

                // If targetPath is an HTML/XHTML cover page, parse it to find the internal image tag
                if (targetPath.endsWith(".xhtml") || targetPath.endsWith(".html") || targetPath.endsWith(".htm")) {
                    val htmlBytes = zipEntriesMap[targetPath]
                        ?: zipEntriesMap.entries.firstOrNull { it.key.endsWith("/$targetPath") || targetPath.endsWith("/${it.key}") }?.value
                    if (htmlBytes != null) {
                        val htmlStr = String(htmlBytes, Charsets.UTF_8)
                        val imgMatch = Regex("<(?:img|image)[^>]+(?:src|href|xlink:href)\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(htmlStr)
                            ?: Regex("url\\(['\"]?([^'\"]+)['\"]?\\)", RegexOption.IGNORE_CASE).find(htmlStr)
                        if (imgMatch != null) {
                            val imgSrc = imgMatch.groupValues[1]
                            val htmlDir = if (targetPath.contains("/")) targetPath.substringBeforeLast("/") else ""
                            targetPath = resolvePath(htmlDir, imgSrc)
                        }
                    }
                }

                coverBytes = zipEntriesMap[targetPath]

                if (coverBytes == null) {
                    coverBytes = zipEntriesMap.entries.firstOrNull {
                        it.key == targetPath || it.key.endsWith("/$targetPath") || targetPath.endsWith("/${it.key}")
                    }?.value
                }

                if (coverBytes == null) {
                    val targetFilename = targetPath.substringAfterLast("/")
                    coverBytes = zipEntriesMap.entries.firstOrNull {
                        it.key.substringAfterLast("/") == targetFilename && it.value.size > 500
                    }?.value
                }
            }

            // Fallback Image Scanner if coverBytes is still null or empty
            if (coverBytes == null || coverBytes.isEmpty()) {
                class ImageCandidate(val key: String, val bytes: ByteArray, val score: Int)

                val imageCandidates = mutableListOf<ImageCandidate>()
                val validExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

                for ((key, bytes) in zipEntriesMap) {
                    val ext = key.substringAfterLast(".", "").lowercase()
                    if (validExtensions.contains(ext) && bytes.size > 300) {
                        var score = 0
                        val lowerKey = key.lowercase()
                        val filename = lowerKey.substringAfterLast("/")

                        if (filename.contains("cover")) score += 120
                        else if (lowerKey.contains("cover")) score += 100

                        if (filename.contains("front")) score += 90
                        else if (lowerKey.contains("front")) score += 70

                        if (filename.contains("jacket") || filename.contains("poster") || filename.contains("title") || filename.contains("wrapper")) score += 60
                        if (lowerKey.contains("images/") || lowerKey.contains("media/") || lowerKey.contains("oebps/")) score += 15
                        if (filename.contains("01") || filename.contains("page1") || filename.contains("001")) score += 20

                        if (bytes.size > 10000) score += 20
                        if (bytes.size > 30000) score += 20

                        imageCandidates.add(ImageCandidate(key, bytes, score))
                    }
                }

                imageCandidates.sortWith(compareByDescending<ImageCandidate> { it.score }.thenByDescending { it.bytes.size })
                if (imageCandidates.isNotEmpty()) {
                    coverBytes = imageCandidates.first().bytes
                }
            }

            if (coverBytes != null && coverBytes.isNotEmpty()) {
                val cacheDir = context.cacheDir
                val coversDir = File(cacheDir, "covers")
                if (!coversDir.exists()) {
                    coversDir.mkdirs()
                }
                val sanitizedSha1 = sha1.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                val coverFile = File(coversDir, "${sanitizedSha1}.jpg")
                coverFile.writeBytes(coverBytes)
                coverFile.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting EPUB cover: ${file.name}", e)
            null
        }
    }
}

