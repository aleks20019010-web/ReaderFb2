package com.nightread.app.data

import android.content.Context
import android.util.Log
import com.nightread.app.data.epub.EpubCoverExtractor
import com.nightread.app.data.epub.EpubPathResolver
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import java.security.MessageDigest
import java.util.UUID
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

    fun cleanZipPath(path: String): String = EpubPathResolver.cleanZipPath(path)

    fun cleanHtmlAndEntities(rawText: String?): String? = EpubPathResolver.cleanHtmlAndEntities(rawText)

    fun computeFileSha1(file: File): String? {
        if (!file.exists() || !file.isFile) return null
        return try {
            file.inputStream().use { fis ->
                val digest = MessageDigest.getInstance("SHA-1")
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compute SHA-1 of local file: ${file.absolutePath}", e)
            null
        }
    }

    fun getEpubMetadata(createInputStream: () -> InputStream?): EpubMetadata? {
        return getEpubMetadataImpl(createInputStream)
    }

    fun getEpubMetadata(bytes: ByteArray): EpubMetadata? {
        return getEpubMetadataImpl({ bytes.inputStream() })
    }

    fun getEpubMetadata(file: File): EpubMetadata? {
        return getEpubMetadataImpl({ file.inputStream() })
    }

    private fun getEpubMetadataImpl(createInputStream: () -> InputStream?): EpubMetadata? {
        return try {
            var opfPath: String? = null
            var opfContent: String? = null
            val zipFiles = mutableMapOf<String, ByteArray>()
            var coverPath: String? = null

            ZipInputStream(createInputStream()!!.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val normalizedName = cleanZipPath(entry.name)
                    val ext = normalizedName.substringAfterLast(".", "").lowercase()
                    if (ext == "opf" || ext == "xml" || normalizedName.contains("meta-inf") ||
                        normalizedName.contains("annotation") || normalizedName.contains("description")) {
                        val buffer = ByteArrayOutputStream()
                        val data = ByteArray(8192)
                        var nRead: Int
                        var totalRead = 0
                        while (zip.read(data, 0, data.size).also { nRead = it } != -1 && totalRead < 512 * 1024) {
                            buffer.write(data, 0, nRead)
                            totalRead += nRead
                        }
                        val content = buffer.toByteArray()
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

                    var descriptionText: String? = null
                    val descMatch = Regex("<(?:\\w+:)?description[^>]*>(.*?)</(?:\\w+:)?description>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).find(opfContent)
                    if (descMatch != null) {
                        descriptionText = cleanHtmlAndEntities(descMatch.groupValues[1])
                    }

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

                    var coverIdOrPath: String? = null
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

                    val manifestMap = mutableMapOf<String, String>()
                    val manifestMediaTypeMap = mutableMapOf<String, String>()
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

                    if (rawCoverPath == null) {
                        val coverKeys = listOf("cover", "cover-image", "coverimage", "cover_image", "title-page", "titlepage", "thumb", "thumbnail")
                        for (key in coverKeys) {
                            if (manifestMap.containsKey(key)) {
                                rawCoverPath = manifestMap[key]
                                break
                            }
                        }
                    }

                    if (rawCoverPath != null) {
                        coverPath = EpubPathResolver.resolvePath(opfDir, rawCoverPath)
                    }

                    val identifier = idMatch?.groupValues?.get(1)?.trim() ?: UUID.randomUUID().toString()

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
                            val fullPath = EpubPathResolver.resolvePath(opfDir, href)
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
                        contentBuilder.append("Книга успешно импортирована.")
                    }

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
            Log.e(TAG, "Error getting EPUB metadata", e)
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

    fun extractAndSaveEpubCover(inputStream: InputStream, coverPath: String?, sha1: String, context: Context): String? {
        return EpubCoverExtractor.extractAndSaveEpubCover({ inputStream }, coverPath, sha1, context)
    }

    fun extractAndSaveEpubCover(bytes: ByteArray, coverPath: String?, sha1: String, context: Context): String? {
        return EpubCoverExtractor.extractAndSaveEpubCover({ bytes.inputStream() }, coverPath, sha1, context)
    }

    fun extractAndSaveEpubCover(createInputStream: () -> InputStream?, coverPath: String?, sha1: String, context: Context): String? {
        return EpubCoverExtractor.extractAndSaveEpubCover(createInputStream, coverPath, sha1, context)
    }

    fun extractAndSaveEpubCover(file: File, coverPath: String?, sha1: String, context: Context): String? {
        return EpubCoverExtractor.extractAndSaveEpubCover({ file.inputStream() }, coverPath, sha1, context)
    }
}
